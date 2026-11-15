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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.Update;
import io.jsondb.tests.updatemodel.UpdateDoc;

/**
 * How findAndModify() and findAllAndModify() convert Update values into document properties.
 * Values are converted with the same Jackson rules used to read the collection files, and an update
 * that names an unknown property or cannot be converted without losing data is rejected.
 */
public class FindAndModifyConversionTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/findAndModifyConversionTests";
  private File dbFilesFolder = new File(dbFilesLocation);

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.updatemodel");
    jsonDBTemplate.createCollection(UpdateDoc.class);
    jsonDBTemplate.insert(newDoc("1"));
    jsonDBTemplate.insert(newDoc("2"));
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  private static UpdateDoc newDoc(String id) {
    UpdateDoc d = new UpdateDoc();
    d.setId(id);
    d.setName("orig");
    d.setSize(100);
    d.setCount(3);
    d.setBoxed(4);
    d.setStatus(UpdateDoc.Status.NEW);
    UpdateDoc.Address address = new UpdateDoc.Address();
    address.setCity("Mumbai");
    d.setAddress(address);
    return d;
  }

  private UpdateDoc modify(String key, Object value) {
    return jsonDBTemplate.findAndModify("/.[id='1']", Update.update(key, value), UpdateDoc.class);
  }

  /**
   * Asserts the update is rejected and that neither memory nor the file changed
   */
  private void assertRejected(String key, Object value, String expectedMessagePart) {
    try {
      modify(key, value);
      fail("Expected the update " + key + "=" + value + " to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue("message was: " + expected.getMessage(), expected.getMessage().contains(expectedMessagePart));
    }
    assertUnchanged(jsonDBTemplate.findById("1", UpdateDoc.class));
    jsonDBTemplate.reLoadDB();
    assertUnchanged(jsonDBTemplate.findById("1", UpdateDoc.class));
  }

  private static void assertUnchanged(UpdateDoc d) {
    assertEquals("orig", d.getName());
    assertEquals(100, d.getSize());
    assertEquals(3, d.getCount());
    assertEquals(Integer.valueOf(4), d.getBoxed());
    assertEquals(UpdateDoc.Status.NEW, d.getStatus());
    assertEquals("Mumbai", d.getAddress().getCity());
  }

  // Conversions that behaved this way before and must keep doing so

  @Test
  public void testSetsStringProperty() {
    assertEquals("renamed", modify("name", "renamed").getName());
    assertEquals("renamed", jsonDBTemplate.findById("1", UpdateDoc.class).getName());
  }

  @Test
  public void testConvertsIntegerToLong() {
    assertEquals(5, modify("size", 5).getSize());
  }

  @Test
  public void testConvertsNumericStringToLong() {
    assertEquals(12345, modify("size", "12345").getSize());
  }

  @Test
  public void testConvertsLongToInt() {
    assertEquals(9, modify("count", 9L).getCount());
  }

  @Test
  public void testConvertsBooleanString() {
    assertTrue(modify("flash", "true").isFlash());
  }

  @Test
  public void testConvertsIntegerToString() {
    assertEquals("42", modify("name", 42).getName());
  }

  @Test
  public void testConvertsNumericStringToBoxedInteger() {
    assertEquals(Integer.valueOf(15), modify("boxed", "15").getBoxed());
  }

  @Test
  public void testSetsBoxedPropertyToNull() {
    assertNull(modify("boxed", null).getBoxed());
  }

  @Test
  public void testSetsEnumProperty() {
    assertEquals(UpdateDoc.Status.DONE, modify("status", UpdateDoc.Status.DONE).getStatus());
  }

  @Test
  public void testSetsNestedProperty() {
    assertEquals("Pune", modify("address.city", "Pune").getAddress().getCity());
    jsonDBTemplate.reLoadDB();
    assertEquals("Pune", jsonDBTemplate.findById("1", UpdateDoc.class).getAddress().getCity());
  }

  @Test
  public void testSetsWholeNestedObject() {
    UpdateDoc.Address address = new UpdateDoc.Address();
    address.setCity("Delhi");
    assertEquals("Delhi", modify("address", address).getAddress().getCity());
  }

  @Test
  public void testFindAllAndModifyConvertsTheSameWay() {
    List<UpdateDoc> modified = jsonDBTemplate.findAllAndModify("/.", Update.update("size", "77"), UpdateDoc.class);
    assertEquals(2, modified.size());
    for (UpdateDoc d : jsonDBTemplate.findAll(UpdateDoc.class)) {
      assertEquals(77, d.getSize());
    }
  }

  // Behaviour that changed: these used to fail with a misleading error or silently store wrong data

  @Test
  public void testConvertsEnumNameString() {
    assertEquals(UpdateDoc.Status.DONE, modify("status", "DONE").getStatus());
  }

  @Test
  public void testRejectsUnknownProperty() {
    assertRejected("nosuch", "x", "nosuch");
  }

  @Test
  public void testRejectsNonNumericStringForInt() {
    assertRejected("count", "x", "count");
  }

  @Test
  public void testRejectsDecimalForLong() {
    assertRejected("size", 7.9, "size");
  }

  @Test
  public void testRejectsNullForPrimitive() {
    assertRejected("size", null, "size");
  }

  @Test
  public void testRejectsNonBooleanString() {
    assertRejected("flash", "yes", "flash");
  }

  @Test
  public void testRejectsNestedPathThroughNonObject() {
    assertRejected("name.first", "x", "name.first");
  }

  @Test
  public void testFindAllAndModifyRejectsWithoutChangingAnyDocument() {
    try {
      jsonDBTemplate.findAllAndModify("/.", Update.update("count", "x"), UpdateDoc.class);
      fail("Expected findAllAndModify to reject the update");
    } catch (InvalidJsonDbApiUsageException expected) {
      // Rejecting is the correct outcome
    }
    for (UpdateDoc d : jsonDBTemplate.findAll(UpdateDoc.class)) {
      assertUnchanged(d);
    }
  }
}
