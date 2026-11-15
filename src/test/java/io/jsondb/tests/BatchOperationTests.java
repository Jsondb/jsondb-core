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
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.Update;
import io.jsondb.tests.model.Site;

/**
 * Edge cases of the batch and find-and-modify/remove operations: empty batches, batches of missing
 * documents and queries that match nothing.
 */
public class BatchOperationTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/batchOperationTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File sitesJson = new File(dbFilesFolder, "sites.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    jsonDBTemplate.createCollection(Site.class);
    jsonDBTemplate.insert(site("s1", "Pune"));
  }

  @After
  public void tearDown() throws Exception {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private static Site site(String id, String location) {
    Site s = new Site();
    s.setId(id);
    s.setLocation(location);
    return s;
  }

  private byte[] fileBytes() throws Exception {
    return java.nio.file.Files.readAllBytes(sitesJson.toPath());
  }

  @Test
  public void testEmptyUpsertBatchWritesNothing() throws Exception {
    byte[] before = fileBytes();
    jsonDBTemplate.upsert(Collections.<Site>emptyList(), Site.class);
    assertArrayEquals(before, fileBytes());
    assertEquals(1, jsonDBTemplate.count(Site.class));
  }

  @Test
  public void testRemovingABatchOfMissingDocumentsReturnsNullAndWritesNothing() throws Exception {
    byte[] before = fileBytes();
    assertNull(jsonDBTemplate.remove(Arrays.asList(site("nope-1", null), site("nope-2", null)), Site.class));
    assertArrayEquals(before, fileBytes());
    assertEquals(1, jsonDBTemplate.count(Site.class));
  }

  @Test
  public void testFindAllAndRemoveWithNoMatchReturnsNull() throws Exception {
    byte[] before = fileBytes();
    assertNull(jsonDBTemplate.findAllAndRemove("/.[location='Nowhere']", Site.class));
    assertArrayEquals(before, fileBytes());
  }

  /**
   * Unlike findAllAndRemove(), which returns null, findAllAndModify() returns an empty list
   */
  @Test
  public void testFindAllAndModifyWithNoMatchReturnsAnEmptyList() {
    assertEquals(0, jsonDBTemplate.findAllAndModify("/.[location='Nowhere']", Update.update("location", "x"), Site.class).size());
    assertEquals("Pune", jsonDBTemplate.findById("s1", Site.class).getLocation());
  }

  @Test
  public void testFindAndModifyWithNoMatchReturnsNull() {
    assertNull(jsonDBTemplate.findAndModify("/.[location='Nowhere']", Update.update("location", "x"), Site.class));
  }

  @Test
  public void testFindAndRemoveWithNoMatchReturnsNull() {
    assertNull(jsonDBTemplate.findAndRemove("/.[location='Nowhere']", Site.class));
    assertEquals(1, jsonDBTemplate.count(Site.class));
  }
}
