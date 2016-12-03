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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
import io.jsondb.tests.model.Site;

/**
 * Junit tests for the remove() apis
 *
 * @version 1.0 08-Oct-2016
 */
public class RemoveTests {

  private String dbFilesLocation = "src/test/resources/dbfiles/removeTests";
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
   * Test to remove a single non-existing object from a collection
   */
  @Test
  public void testRemove_NonExistingObject() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Objects with Id 000012 not found in collection instances");

    Instance instance = new Instance();
    instance.setId("000012");

    jsonDBTemplate.remove(instance, Instance.class);
  }

  /**
   * Test to remove a null object from a collection
   */
  @Test
  public void testRemove_NullObject() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Null Object cannot be removed from DB");
    Object nullObject = null;
    jsonDBTemplate.remove(nullObject, Instance.class);
  }
  
  /**
   * Test to remove a null object from a collection
   */
  @Test
  public void testRemove_NullObjectBatch() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Null Object batch cannot be removed from DB");

    jsonDBTemplate.remove(null, Instance.class);
  }

  /**
   * Test to remove a object from a non-existent collection
   */
  @Test
  public void testRemove_FromNonExistingCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");

    Site s = new Site();
    s.setId("000012");

    jsonDBTemplate.remove(s, Site.class);
  }

  /**
   * Test to remove a object from a non-existent collection
   */
  @Test
  public void testRemoveBatch_FromNonExistingCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");

    Site s = new Site();
    s.setId("000012");
    List<Site> ss = new ArrayList<Site>();
    ss.add(s);

    jsonDBTemplate.remove(ss, "sites");
  }
  
  /**
   * Test to remove a single object from a collection
   */
  @Test
  public void testRemove_ValidObject() {
    List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
    int size = instances.size();

    Instance instance = new Instance();
    instance.setId("05");

    Instance removedObject = jsonDBTemplate.remove(instance, Instance.class);

    instances = jsonDBTemplate.getCollection(Instance.class);
    assertNotNull(instances);
    assertEquals(size-1, instances.size());
    assertNotNull(removedObject);
    assertEquals("05", removedObject.getId());
  }

  /**
   * Test to remove a batch of objects from collection
   */
  @Test
  public void testRemove_BatchOfObjects() {
    List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
    int size = instances.size();

    List<Instance> batch = new ArrayList<Instance>();
    for (int i=1; i<3; i++) {
      Instance e = new Instance();
      e.setId(String.format("%02d", i));
      batch.add(e);
    }

    List<Instance> removedObjects = jsonDBTemplate.remove(batch, Instance.class);

    instances = jsonDBTemplate.getCollection(Instance.class);
    assertNotNull(instances);
    assertEquals(size-2, instances.size());
    assertNotNull(removedObjects);
    assertEquals(2, removedObjects.size());
  }
}
