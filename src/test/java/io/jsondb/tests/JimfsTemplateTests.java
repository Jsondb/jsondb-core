/*
 * Copyright (c) 2026 Farooq Khan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jsondb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.query.ddl.RenameOperation;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.LoadBalancer;

/**
 * A database on a file system other than the default one, here Jimfs, Google's in-memory file system,
 * opened through the Path constructors (issue #17). The other suites keep testing on disk.
 */
public class JimfsTemplateTests {
  private static final String WARNING = "is already open by another JsonDBTemplate";

  private FileSystem fs;
  private Path dbFilesPath;
  private List<JsonDBTemplate> opened = new ArrayList<JsonDBTemplate>();

  @Before
  public void setUp() throws Exception {
    fs = Jimfs.newFileSystem(Configuration.unix());
    dbFilesPath = fs.getPath("/db");
  }

  @After
  public void tearDown() throws Exception {
    for (JsonDBTemplate template : opened) {
      template.close();
    }
    fs.close();
  }

  private JsonDBTemplate open(JsonDBTemplate template) {
    opened.add(template);
    return template;
  }

  private void copyFixture(String fileName) throws Exception {
    Files.createDirectories(dbFilesPath);
    Files.copy(Paths.get("src/test/resources/dbfiles", fileName), dbFilesPath.resolve(fileName));
  }

  private interface Action {
    void run() throws Exception;
  }

  /**
   * Runs the action and returns what was logged to System.err while it ran
   */
  private String loggedDuring(Action action) throws Exception {
    PrintStream original = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, "UTF-8"));
    try {
      action.run();
    } finally {
      System.setErr(original);
    }
    return captured.toString("UTF-8");
  }

  @Test
  public void configKeepsThePath() {
    JsonDBConfig config = new JsonDBConfig(dbFilesPath, "io.jsondb.tests.model", null, false, null);
    assertSame(dbFilesPath, config.getDbFilesPath());
    assertEquals("/db", config.getDbFilesLocationString());
  }

  @Test
  public void opensAFolderThatDoesNotExistYet() throws Exception {
    JsonDBTemplate template = open(new JsonDBTemplate(dbFilesPath,
        Collections.<Class<?>>singletonList(Instance.class)));
    assertTrue(Files.isDirectory(dbFilesPath));
    assertTrue(Files.isDirectory(dbFilesPath.resolve("lock")));

    template.createCollection(Instance.class);
    Instance instance = new Instance();
    instance.setId("07");
    instance.setHostname("ec2-54-191-07");
    template.insert(instance);

    assertTrue(Files.isRegularFile(dbFilesPath.resolve("instances.json")));
    assertEquals("ec2-54-191-07", template.findById("07", Instance.class).getHostname());
  }

  @Test
  public void changesToACollectionSurviveReopening() throws Exception {
    copyFixture("instances.json");
    DefaultAESCBCCipher cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
    JsonDBTemplate template = open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model", cipher));
    int count = template.findAll(Instance.class).size();

    Instance first = template.findById("01", Instance.class);
    first.setHostname("renamed");
    template.save(first, Instance.class);
    template.remove(template.findById("02", Instance.class), Instance.class);
    template.close();

    JsonDBTemplate reopened = open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model", cipher));
    assertEquals(count - 1, reopened.findAll(Instance.class).size());
    assertEquals("renamed", reopened.findById("01", Instance.class).getHostname());
    assertNull(reopened.findById("02", Instance.class));
    assertEquals(first.getPrivateKey(), reopened.findById("01", Instance.class).getPrivateKey());
  }

  @Test
  public void reloadsACollectionChangedOutsideJsonDB() throws Exception {
    copyFixture("instances.json");
    JsonDBTemplate template = open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model"));
    Files.write(dbFilesPath.resolve("instances.json"), "{\"schemaVersion\":\"1.0\"}\n".getBytes(StandardCharsets.UTF_8));

    template.reloadCollection("instances");

    assertTrue(template.findAll(Instance.class).isEmpty());
  }

  @Test
  public void dropsACollection() throws Exception {
    copyFixture("instances.json");
    JsonDBTemplate template = open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model"));

    template.dropCollection(Instance.class);

    assertFalse(template.collectionExists(Instance.class));
    assertFalse(Files.exists(dbFilesPath.resolve("instances.json")));
  }

  @Test
  public void updatesACollectionSchema() throws Exception {
    Files.createDirectories(dbFilesPath);
    Files.write(dbFilesPath.resolve("loadbalancer.json"), (
        "{\"schemaVersion\":\"0.5\"}\n"
        + "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"user\":\"admin1\"}\n").getBytes(StandardCharsets.UTF_8));
    JsonDBTemplate template = open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model", null, true, null));
    assertTrue(template.isCollectionReadonly(LoadBalancer.class));

    template.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username")),
        LoadBalancer.class);

    assertFalse(template.isCollectionReadonly(LoadBalancer.class));
    assertEquals("admin1", template.findById("001", LoadBalancer.class).getUsername());
  }

  @Test
  public void rejectsAFolderThatIsAFile() throws Exception {
    Files.createFile(dbFilesPath);
    try {
      open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model"));
      fail("Expected a regular file to be rejected as the database folder");
    } catch (InvalidJsonDbApiUsageException expected) {
      // Rejecting is the correct outcome
    }
  }

  @Test
  public void sameFolderOpenedTwiceWarns() throws Exception {
    open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model"));
    String logged = loggedDuring(() -> open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model")));
    assertTrue("expected a warning, logged: " + logged, logged.contains(WARNING));
  }

  /**
   * Folders with the same name on two file systems are different folders
   */
  @Test
  public void sameFolderNameOnAnotherFileSystemDoesNotWarn() throws Exception {
    open(new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model"));
    try (FileSystem other = Jimfs.newFileSystem(Configuration.unix())) {
      String logged = loggedDuring(() -> open(new JsonDBTemplate(other.getPath("/db"), "io.jsondb.tests.model")).close());
      assertFalse("unexpected warning: " + logged, logged.contains(WARNING));
    }
  }
}
