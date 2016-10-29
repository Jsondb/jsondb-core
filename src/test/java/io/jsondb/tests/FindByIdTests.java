/*
 * Copyright (c) 2016 Farooq Khan
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
import static org.junit.Assert.assertNull;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.tests.model.Instance;

/**
 * @version 1.0 14-Oct-2016
 */
public class FindByIdTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/findByIdTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    ICipher cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  /**
   * test to find a document with a existing id.
   */
  @Test
  public void testFindById_ForExistingId() {
    Instance instance = jsonDBTemplate.findById("01", Instance.class);
    assertNotNull(instance);
    assertEquals(instance.getId(), "01");
  }

  /**
   * test to find a document with a non-existent id.
   */
  @Test
  public void testFindById_ForNonExistentId() {
    Instance instance = jsonDBTemplate.findById("00", Instance.class);
    assertNull(instance);
  }

  private class NonAnotatedClass {}

  /**
   * test to find a document formEntity type which does not have @Document annotation
   */
  @Test
  public void testFindById_NonAnotatedClass() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Entity 'NonAnotatedClass' is not annotated with annotation @Document");
    jsonDBTemplate.findById("000000", NonAnotatedClass.class);
  }

  /**
   * test to find a document for a unknown collection name
   */
  @Test
  public void testFindById_UnknownCollectionName() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'SomeCollection' not found. Create collection first");
    jsonDBTemplate.findById("000000", "SomeCollection");
  }
}
