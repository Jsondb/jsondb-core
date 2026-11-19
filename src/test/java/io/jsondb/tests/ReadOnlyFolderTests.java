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
import java.util.Arrays;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Site;

/**
 * A database in a folder that cannot be written to, with no lock folder in it (#111)
 */
public class ReadOnlyFolderTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/readOnlyFolderTests";
  private File dbFilesFolder = new File(dbFilesLocation);

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    try (JsonDBTemplate writable = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model")) {
      writable.createCollection(Site.class);
      writable.insert(Arrays.asList(ReadOnlyModeTests.site("1", "one"), ReadOnlyModeTests.site("2", "two")), Site.class);
    }
    Util.delete(new File(dbFilesFolder, "lock"));

    dbFilesFolder.setWritable(false);
    // A superuser can write to a read-only folder, which would make every test here meaningless
    Assume.assumeFalse("running with permission to write to read-only folders", dbFilesFolder.canWrite());
  }

  @After
  public void tearDown() {
    dbFilesFolder.setWritable(true);
    Util.delete(dbFilesFolder);
  }

  @Test
  public void testReadOnlyModeReads() {
    try (JsonDBTemplate template = new JsonDBTemplate(ReadOnlyModeTests.readOnlyConfig(dbFilesLocation))) {
      assertEquals(2, template.findAll(Site.class).size());
      assertEquals("one", template.findById("1", Site.class).getLocation());
    }
    assertFalse(new File(dbFilesFolder, "lock").exists());
  }

  @Test
  public void testWithoutReadOnlyModeFailsClearly() {
    try {
      new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
      fail("a read-only database folder was opened as an empty database");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("is not writable"));
      assertTrue(expected.getMessage(), expected.getMessage().contains("setReadOnly(true)"));
    }
  }
}
