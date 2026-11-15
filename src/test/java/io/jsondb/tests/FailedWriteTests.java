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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.JsonDBException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.Update;
import io.jsondb.tests.model.Site;

/**
 * A write that cannot reach the collection file, here because the database folder is read-only so no
 * temporary file can be created, must fail with a JsonDBException and leave memory and file unchanged.
 * Most operations used to return normally instead, so callers believed a write had been saved.
 */
public class FailedWriteTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/failedWriteTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File sitesJson = new File(dbFilesFolder, "sites.json");

  private JsonDBTemplate jsonDBTemplate = null;
  private byte[] fileBefore;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    jsonDBTemplate.createCollection(Site.class);
    jsonDBTemplate.insert(Arrays.asList(site("1", "one"), site("2", "two")), Site.class);
    fileBefore = Files.readAllBytes(sitesJson.toPath());

    dbFilesFolder.setWritable(false);
    // A superuser can write to a read-only folder, which would make every test here meaningless
    Assume.assumeFalse("running with permission to write to read-only folders", dbFilesFolder.canWrite());
  }

  @After
  public void tearDown() throws Exception {
    dbFilesFolder.setWritable(true);
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private static Site site(String id, String location) {
    Site s = new Site();
    s.setId(id);
    s.setLocation(location);
    return s;
  }

  private interface Write {
    void run();
  }

  private void assertFailsAndChangesNothing(Write write) throws Exception {
    try {
      write.run();
      fail("a write that could not be saved returned normally");
    } catch (JsonDBException expected) {
      // the write was reported as failed
    }
    List<Site> all = jsonDBTemplate.findAll(Site.class);
    assertEquals(2, all.size());
    assertEquals("one", jsonDBTemplate.findById("1", Site.class).getLocation());
    assertEquals("two", jsonDBTemplate.findById("2", Site.class).getLocation());
    assertEquals(Arrays.toString(fileBefore), Arrays.toString(Files.readAllBytes(sitesJson.toPath())));
    for (String name : dbFilesFolder.list()) {
      assertNotNull(name);
      assertEquals(true, name.equals("sites.json") || name.equals("lock"));
    }
  }

  @Test
  public void testInsert() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.insert(site("3", "three")));
  }

  @Test
  public void testBatchInsert() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.insert(Arrays.asList(site("3", "three")), Site.class));
  }

  @Test
  public void testSave() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.save(site("1", "changed"), Site.class));
  }

  @Test
  public void testUpsertOfANewDocument() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.upsert(site("3", "three")));
  }

  @Test
  public void testUpsertOfAnExistingDocument() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.upsert(site("1", "changed")));
  }

  @Test
  public void testBatchUpsert() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.upsert(Arrays.asList(site("1", "changed"), site("3", "three")), Site.class));
  }

  @Test
  public void testRemove() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.remove(site("1", "one"), Site.class));
  }

  @Test
  public void testBatchRemove() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.remove(Arrays.asList(site("1", "one")), Site.class));
  }

  @Test
  public void testFindAndRemove() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.findAndRemove("/.[id='1']", Site.class));
  }

  @Test
  public void testFindAllAndRemove() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.findAllAndRemove("/.[id='1']", Site.class));
  }

  @Test
  public void testFindAndModify() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.findAndModify("/.[id='1']", Update.update("location", "changed"), Site.class));
  }

  @Test
  public void testFindAllAndModify() throws Exception {
    assertFailsAndChangesNothing(() -> jsonDBTemplate.findAllAndModify("/.", Update.update("location", "changed"), Site.class));
  }
}
