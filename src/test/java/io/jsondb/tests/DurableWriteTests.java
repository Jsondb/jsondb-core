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

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Site;

/**
 * Forcing the database folder to disk after each write is off by default, because it makes every write
 * many times slower, and can be turned on for applications that must not lose the last write in a crash
 */
public class DurableWriteTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/durableWriteTests";
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

  private static Site site(String id) {
    Site s = new Site();
    s.setId(id);
    s.setLocation("loc-" + id);
    return s;
  }

  @Test
  public void testFolderSyncIsOffByDefault() {
    assertFalse(jsonDBTemplate.isSyncFolderOnWrite());
  }

  @Test
  public void testWritesWithFolderSyncOnAreStoredAndReloaded() {
    jsonDBTemplate.setSyncFolderOnWrite(true);
    assertTrue(jsonDBTemplate.isSyncFolderOnWrite());

    jsonDBTemplate.insert(site("1"));
    jsonDBTemplate.upsert(site("2"));
    jsonDBTemplate.remove(jsonDBTemplate.findById("1", Site.class), Site.class);

    jsonDBTemplate.reLoadDB();
    assertEquals(1, jsonDBTemplate.findAll(Site.class).size());
    assertEquals("loc-2", jsonDBTemplate.findById("2", Site.class).getLocation());
    for (String name : dbFilesFolder.list()) {
      assertTrue("unexpected file " + name, name.equals("sites.json") || name.equals("lock"));
    }
  }
}
