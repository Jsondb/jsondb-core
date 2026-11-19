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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.ddl.AddOperation;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.query.ddl.DeleteOperation;
import io.jsondb.query.ddl.RenameOperation;
import io.jsondb.tests.model.LoadBalancer;
import io.jsondb.tests.util.TestUtils;

/**
 * Schema updates in strict mode (compatibilityMode=false), where a file with keys the class no longer
 * has fails to load. The update must still be able to migrate it (#84).
 */
public class StrictModeSchemaUpdateTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/strictModeSchemaUpdateTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File loadbalancerJson = new File(dbFilesFolder, "loadbalancer.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
  }

  @After
  public void tearDown() throws Exception {
    if (jsonDBTemplate != null) {
      jsonDBTemplate.close();
    }
    Util.delete(dbFilesFolder);
  }

  /**
   * Writes loadbalancer.json at schema 0.5 with the given key for LoadBalancer's "username" and an
   * extra "deletedField", then opens it in strict mode
   */
  private void openOldSchemaFile(String usernameKey) throws Exception {
    List<String> lines = new ArrayList<String>();
    lines.add("{\"schemaVersion\":\"0.5\"}");
    for (int i = 1; i <= 3; i++) {
      lines.add(String.format("{\"id\":\"%03d\",\"hostname\":\"eclb-54-%02d\",\"%s\":\"admin%d\",\"deletedField\":\"yes\"}",
          i, i, usernameKey, i));
    }
    Files.write(loadbalancerJson.toPath(), lines, StandardCharsets.UTF_8);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", null, false, null);
  }

  @Test
  public void testOldFileDoesNotLoadInStrictMode() throws Exception {
    openOldSchemaFile("user");
    assertFalse(jsonDBTemplate.collectionExists(LoadBalancer.class));
  }

  @Test
  public void testRenameAndDelete() throws Exception {
    openOldSchemaFile("user");

    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username"))
        .set("deletedField", new DeleteOperation()), LoadBalancer.class);

    assertTrue(jsonDBTemplate.collectionExists(LoadBalancer.class));
    assertFalse(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));
    assertEquals(3, jsonDBTemplate.findAll(LoadBalancer.class).size());
    assertEquals("admin2", jsonDBTemplate.findById("002", LoadBalancer.class).getUsername());
    TestUtils.checkLastLines(loadbalancerJson, new String[] {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"username\":\"admin1\",\"osName\":null}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"username\":\"admin2\",\"osName\":null}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"username\":\"admin3\",\"osName\":null}"});
  }

  @Test
  public void testAddAndDelete() throws Exception {
    openOldSchemaFile("username");

    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("osName", new AddOperation("linux", false))
        .set("deletedField", new DeleteOperation()), LoadBalancer.class);

    assertTrue(jsonDBTemplate.collectionExists(LoadBalancer.class));
    assertEquals("linux", jsonDBTemplate.findById("001", LoadBalancer.class).getOsName());
    assertEquals("admin3", jsonDBTemplate.findById("003", LoadBalancer.class).getUsername());
  }

  @Test
  public void testDeleteOnly() throws Exception {
    openOldSchemaFile("username");

    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("deletedField", new DeleteOperation()),
        LoadBalancer.class);

    assertTrue(jsonDBTemplate.collectionExists(LoadBalancer.class));
    assertEquals(3, jsonDBTemplate.findAll(LoadBalancer.class).size());
  }

  /**
   * An update that is rejected leaves the file as it was, and the collection still unloaded
   */
  @Test
  public void testRejectedUpdateChangesNothing() throws Exception {
    openOldSchemaFile("user");
    byte[] before = Files.readAllBytes(loadbalancerJson.toPath());
    try {
      jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("login")),
          LoadBalancer.class);
      fail("a rename to a property the class does not have was accepted");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("has no property 'login'"));
    }
    assertArrayEquals(before, Files.readAllBytes(loadbalancerJson.toPath()));
    assertFalse(jsonDBTemplate.collectionExists(LoadBalancer.class));
  }

  /**
   * Without a file there is still nothing to update
   */
  @Test
  public void testMissingCollectionIsStillRejected() throws Exception {
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", null, false, null);
    try {
      jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username")),
          LoadBalancer.class);
      fail("an update of a collection with no file was accepted");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("not found"));
    }
    assertFalse(loadbalancerJson.exists());
  }
}
