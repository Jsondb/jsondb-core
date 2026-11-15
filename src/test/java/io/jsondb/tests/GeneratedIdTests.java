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

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Site;

/**
 * Documents inserted without an id get a generated UUID. The generated id must also be the key the
 * document is stored under: it used to be stored under null, so a second document without an id
 * replaced the first in memory and the next write dropped it from the file.
 */
public class GeneratedIdTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/generatedIdTests";
  private File dbFilesFolder = new File(dbFilesLocation);

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    jsonDBTemplate.createCollection(Site.class);
  }

  @After
  public void tearDown() throws Exception {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private static Site siteWithoutId(String location) {
    Site s = new Site();
    s.setLocation(location);
    return s;
  }

  /**
   * Asserts the collection holds one document per location, each with a distinct generated id that
   * findById() finds, both in memory and after reloading from disk
   */
  private void assertEveryDocumentKept(String... locations) {
    for (int pass = 0; pass < 2; pass++) {
      List<Site> all = jsonDBTemplate.findAll(Site.class);
      assertEquals(locations.length, all.size());
      Set<String> ids = new HashSet<String>();
      for (Site s : all) {
        assertNotNull(s.getId());
        ids.add(s.getId());
        assertEquals(s.getLocation(), jsonDBTemplate.findById(s.getId(), Site.class).getLocation());
      }
      assertEquals(locations.length, ids.size());
      Set<String> found = new HashSet<String>();
      for (Site s : all) {
        found.add(s.getLocation());
      }
      assertEquals(new HashSet<String>(Arrays.asList(locations)), found);
      jsonDBTemplate.reLoadDB();
    }
  }

  @Test
  public void testSingleInsertsWithoutIdKeepEveryDocument() {
    jsonDBTemplate.insert(siteWithoutId("A"));
    jsonDBTemplate.insert(siteWithoutId("B"));
    jsonDBTemplate.insert(siteWithoutId("C"));
    assertEveryDocumentKept("A", "B", "C");
  }

  @Test
  public void testBatchInsertWithoutIdsKeepsEveryDocument() {
    jsonDBTemplate.insert(Arrays.asList(siteWithoutId("A"), siteWithoutId("B")), Site.class);
    jsonDBTemplate.insert(siteWithoutId("C"));
    assertEveryDocumentKept("A", "B", "C");
  }

  @Test
  public void testUpsertsWithoutIdKeepEveryDocument() {
    jsonDBTemplate.upsert(siteWithoutId("A"));
    jsonDBTemplate.upsert(siteWithoutId("B"));
    jsonDBTemplate.upsert(siteWithoutId("C"));
    assertEveryDocumentKept("A", "B", "C");
  }

  @Test
  public void testBatchUpsertWithoutIdsKeepsEveryDocument() {
    jsonDBTemplate.upsert(Arrays.asList(siteWithoutId("A"), siteWithoutId("B")), Site.class);
    jsonDBTemplate.upsert(siteWithoutId("C"));
    assertEveryDocumentKept("A", "B", "C");
  }
}
