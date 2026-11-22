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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.jsondb.CollectionMetaData;
import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.io.JsonReader;
import io.jsondb.io.JsonWriter;
import io.jsondb.tests.model.Instance;

/**
 * Tests for the java.nio Path versions of the file helpers (issue #17), and that writing through
 * them leaves the collection files as before
 */
public class NioPathTests {

  private String dbFilesLocation = "src/test/resources/dbfiles/nioPathTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private Path dbFilesPath = Paths.get(dbFilesLocation);
  private Path instancesJson = dbFilesPath.resolve("instances.json");

  private JsonDBConfig dbConfig;

  @Before
  public void setUp() throws Exception {
    Files.createDirectories(dbFilesPath);
    Files.copy(Paths.get("src/test/resources/dbfiles/instances.json"), instancesJson);
    dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false,
        new DefaultSchemaVersionComparator());
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  @Test
  public void readerReadsAPath() throws IOException {
    JsonReader jr = new JsonReader(dbConfig, instancesJson);
    try {
      assertEquals("{\"schemaVersion\":\"1.0\"}", jr.readLine());
    } finally {
      jr.close();
    }
    assertTrue(Files.isRegularFile(dbFilesPath.resolve("lock").resolve("instances.json.lock")));
  }

  @Test
  public void readerWithoutLockCreatesNoLockFolder() throws IOException {
    JsonReader jr = new JsonReader(dbConfig, instancesJson.toFile(), false);
    try {
      assertEquals("{\"schemaVersion\":\"1.0\"}", jr.readLine());
    } finally {
      jr.close();
    }
    assertFalse(Files.exists(dbFilesPath.resolve("lock")));
  }

  @Test
  public void writerWritesAPath() throws IOException {
    CollectionMetaData cmd = new CollectionMetaData("instances", Instance.class, "1.0", null);
    Instance instance = new Instance();
    instance.setId("99");
    instance.setHostname("nio");

    assertTrue(new JsonWriter(dbConfig, cmd, "instances", instancesJson)
        .reWriteJsonFile(Arrays.asList(instance), false));

    List<String> lines = Files.readAllLines(instancesJson, StandardCharsets.UTF_8);
    assertEquals(2, lines.size());
    assertEquals("{\"schemaVersion\":\"1.0\"}", lines.get(0));
    assertTrue(lines.get(1), lines.get(1).contains("\"hostname\":\"nio\""));
    assertEquals("only the collection file and the lock folder are left", Arrays.asList("instances.json", "lock"),
        listNames(dbFilesPath));
  }

  @Test
  public void writerAndReaderWorkOnAnotherFileSystem() throws IOException {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path folder = Files.createDirectories(fs.getPath("/db"));
      Path collectionFile = folder.resolve("instances.json");
      assertTrue(Util.stampVersion(dbConfig, collectionFile, "1.0"));

      CollectionMetaData cmd = new CollectionMetaData("instances", Instance.class, "1.0", null);
      Instance instance = new Instance();
      instance.setId("101");
      instance.setHostname("jimfs");
      // The write goes through a FileChannel there, as a FileOutputStream only works on the disk
      assertTrue(new JsonWriter(dbConfig, cmd, "instances", collectionFile)
          .reWriteJsonFile(Arrays.asList(instance), false));
      assertEquals("the temporary file is moved into place", Arrays.asList("instances.json", "lock"),
          listNames(folder));

      JsonReader jr = new JsonReader(dbConfig, collectionFile);
      try {
        assertEquals("{\"schemaVersion\":\"1.0\"}", jr.readLine());
        assertTrue(jr.readLine().contains("\"hostname\":\"jimfs\""));
      } finally {
        jr.close();
      }
      assertTrue(Files.isRegularFile(folder.resolve("lock").resolve("instances.json.lock")));
    }
    assertFalse("nothing is written to the disk", Files.exists(dbFilesPath.resolve("lock")));
  }

  @Test
  public void stampVersionWritesAPath() throws IOException {
    Path newCollection = dbFilesPath.resolve("fresh.json");
    assertTrue(Util.stampVersion(dbConfig, newCollection, "2.5"));
    assertEquals(Arrays.asList("{\"schemaVersion\":\"2.5\"}"), Files.readAllLines(newCollection, StandardCharsets.UTF_8));
  }

  @Test
  public void writtenCollectionFileKeepsDefaultPermissions() throws IOException {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    Path plain = Files.createFile(dbFilesPath.resolve("plain.txt"));
    Set<PosixFilePermission> expected = Files.getPosixFilePermissions(plain);

    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    try {
      Instance instance = new Instance();
      instance.setId("100");
      instance.setHostname("perm");
      // Every write replaces the collection file with a new temporary file, so this checks that file
      jsonDBTemplate.insert(instance);
    } finally {
      jsonDBTemplate.close();
    }
    assertEquals(expected, Files.getPosixFilePermissions(instancesJson));
  }

  private static List<String> listNames(Path folder) throws IOException {
    List<String> names = new ArrayList<String>();
    try (Stream<Path> entries = Files.list(folder)) {
      entries.forEach(p -> names.add(p.getFileName().toString()));
    }
    names.sort(null);
    return names;
  }

  @Test
  public void deleteRemovesAFolderTree() throws IOException {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path folder = Files.createDirectories(fs.getPath("/tree/a/b"));
      Files.createFile(folder.resolve("c.json"));
      Files.createFile(fs.getPath("/tree/d.json"));

      Util.delete(fs.getPath("/tree"));

      assertFalse(Files.exists(fs.getPath("/tree")));
      Util.delete(fs.getPath("/tree"));
    }
  }

  /**
   * Deleting a folder that holds a symbolic link to another folder removes the link, not what it points to
   */
  @Test
  public void deleteDoesNotFollowSymbolicLinks() throws IOException {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path keep = Files.createDirectories(fs.getPath("/keep"));
      Files.createFile(keep.resolve("instances.json"));
      Path tree = Files.createDirectories(fs.getPath("/tree"));
      Files.createSymbolicLink(tree.resolve("link"), keep);

      Util.delete(tree);

      assertFalse(Files.exists(tree));
      assertTrue(Files.exists(keep.resolve("instances.json")));
    }
  }
}
