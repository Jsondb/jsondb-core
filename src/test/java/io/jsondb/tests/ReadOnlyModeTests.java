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
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.query.Update;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.query.ddl.RenameOperation;
import io.jsondb.tests.model.Site;

/**
 * A database opened read-only reads normally and rejects every write. The folder here is writable, so
 * the rejections come from the mode and not from file permissions.
 */
public class ReadOnlyModeTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/readOnlyModeTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File sitesJson = new File(dbFilesFolder, "sites.json");
  private File lockFolder = new File(dbFilesFolder, "lock");

  private JsonDBTemplate jsonDBTemplate = null;
  private byte[] fileBefore;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    try (JsonDBTemplate writable = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model")) {
      writable.createCollection(Site.class);
      writable.insert(Arrays.asList(site("1", "one"), site("2", "two")), Site.class);
    }
    Util.delete(lockFolder);
    fileBefore = Files.readAllBytes(sitesJson.toPath());

    jsonDBTemplate = new JsonDBTemplate(readOnlyConfig(dbFilesLocation));
  }

  @After
  public void tearDown() throws Exception {
    if (jsonDBTemplate != null) {
      jsonDBTemplate.close();
    }
    Util.delete(dbFilesFolder);
  }

  static JsonDBConfig readOnlyConfig(String location) {
    JsonDBConfig config = new JsonDBConfig(location, "io.jsondb.tests.model", null, false, null);
    config.setReadOnly(true);
    return config;
  }

  static Site site(String id, String location) {
    Site s = new Site();
    s.setId(id);
    s.setLocation(location);
    return s;
  }

  private interface Write {
    void run() throws Exception;
  }

  private void assertRejectedAndChangesNothing(Write write) throws Exception {
    try {
      write.run();
      fail("a write to a read-only database was accepted");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("read-only"));
    }
    assertEquals(2, jsonDBTemplate.findAll(Site.class).size());
    assertEquals("one", jsonDBTemplate.findById("1", Site.class).getLocation());
    assertEquals(Arrays.toString(fileBefore), Arrays.toString(Files.readAllBytes(sitesJson.toPath())));
    assertEquals(Arrays.asList("sites.json"), Arrays.asList(dbFilesFolder.list()));
  }

  @Test
  public void testReadsWork() {
    assertTrue(jsonDBTemplate.isReadOnly());
    assertTrue(jsonDBTemplate.collectionExists(Site.class));
    assertTrue(jsonDBTemplate.isCollectionReadonly(Site.class));
    assertEquals(2, jsonDBTemplate.findAll(Site.class).size());
    assertEquals(2, jsonDBTemplate.count(Site.class));
    assertEquals("two", jsonDBTemplate.findById("2", Site.class).getLocation());
    List<Site> found = jsonDBTemplate.find("/.[location='one']", Site.class);
    assertEquals(1, found.size());
    assertEquals("1", jsonDBTemplate.findOne("/.[location='one']", Site.class).getId());
  }

  @Test
  public void testNoLockFolderIsCreated() {
    jsonDBTemplate.findAll(Site.class);
    jsonDBTemplate.reloadCollection("sites");
    assertFalse(lockFolder.exists());
  }

  @Test
  public void testReloadSeesChangesMadeElsewhere() throws Exception {
    jsonDBTemplate.close();
    jsonDBTemplate = null;
    try (JsonDBTemplate writable = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model")) {
      writable.insert(site("3", "three"));
    }
    Util.delete(lockFolder);
    jsonDBTemplate = new JsonDBTemplate(readOnlyConfig(dbFilesLocation));
    assertEquals(3, jsonDBTemplate.findAll(Site.class).size());
  }

  @Test
  public void testMissingFolderIsNotCreated() {
    File missing = new File(dbFilesFolder, "missing");
    try {
      new JsonDBTemplate(readOnlyConfig(missing.toString()));
      fail("a read-only database was opened on a folder that does not exist");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("does not exist"));
    }
    assertFalse(missing.exists());
  }

  @Test
  public void testInsert() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.insert(site("3", "three")));
  }

  @Test
  public void testInsertBatch() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.insert(Arrays.asList(site("3", "three")), Site.class));
  }

  @Test
  public void testSave() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.save(site("1", "changed"), Site.class));
  }

  @Test
  public void testUpsert() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.upsert(site("3", "three")));
  }

  @Test
  public void testUpsertBatch() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.upsert(Arrays.asList(site("1", "changed")), Site.class));
  }

  @Test
  public void testRemove() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.remove(site("1", "one"), Site.class));
  }

  @Test
  public void testRemoveBatch() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.remove(Arrays.asList(site("1", "one")), Site.class));
  }

  @Test
  public void testFindAndRemove() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.findAndRemove("/.[id='1']", Site.class));
  }

  @Test
  public void testFindAllAndRemove() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.findAllAndRemove("/.", Site.class));
  }

  @Test
  public void testFindAndModify() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.findAndModify("/.[id='1']", Update.update("location", "changed"), Site.class));
  }

  @Test
  public void testFindAllAndModify() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.findAllAndModify("/.", Update.update("location", "changed"), Site.class));
  }

  @Test
  public void testCreateCollection() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.createCollection("instances"));
  }

  @Test
  public void testDropCollection() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.dropCollection(Site.class));
  }

  @Test
  public void testUpdateCollectionSchema() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.updateCollectionSchema(
        CollectionSchemaUpdate.update("location", new RenameOperation("place")), Site.class));
  }

  @Test
  public void testChangeEncryption() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.changeEncryption(new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==")));
  }

  @Test
  public void testRestore() throws Exception {
    assertRejectedAndChangesNothing(() -> jsonDBTemplate.restore(new File(dbFilesFolder, "backup.zip").getPath(), false));
  }
}
