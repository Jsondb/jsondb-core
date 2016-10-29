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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.security.GeneralSecurityException;
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

/**
 * Unit tests that cover all aspects of find queries.
 *
 * @author Farooq Khan
 * @version 1.0 31 Dec 2015
 */
public class FindQueryTests {

  private String dbFilesLocation = "src/test/resources/dbfiles/findQueryTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    ICipher cipher = null;
    try {
      cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
    } catch (GeneralSecurityException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  /**
   * test to find all documents for a collection type
   */
  @Test
  public void testFind_AllDocumentsForType() {
    String jxQuery = "."; //XPATH for all elements in a collection
    List<Instance> instances = jsonDBTemplate.find(jxQuery, Instance.class);
    assertEquals(instances.size(), 6);
  }

  /**
   * test to find a existing document
   */
  @Test
  public void testFind_DocumentThatExists() {
    String jxQuery = String.format("/.[id='%s']", "01");
    List<Instance> instances = jsonDBTemplate.find(jxQuery, Instance.class);
    assertEquals(instances.size(), 1);
  }

  /**
   * test to find a non-existent document
   */
  @Test
  public void testFind_DocumentThatDoesNotExist() {
    String jxQuery = String.format("/.[id='%s']", "00");
    List<Instance> instances = jsonDBTemplate.find(jxQuery, Instance.class);
    assertNotNull(instances);
    assertEquals(instances.size(), 0);
  }

  /**
   * test to find all documents for a valid collection type
   */
  @Test
  public void testFindAll_ForValidCollectionType() {
    List<Instance> instances = jsonDBTemplate.findAll(Instance.class);
    assertEquals(instances.size(), 6);
  }

  private class NonAnotatedClass {}

  /**
   * test to find all documents for a Entity type which does not have @Document annotation
   */
  @Test
  public void testFindAll_ForInvalidCollectionType() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Entity 'NonAnotatedClass' is not annotated with annotation @Document");
    jsonDBTemplate.findAll(NonAnotatedClass.class);
  }

  /**
   * test to find all documents for a unknown collection name
   */
  @Test
  public void testFindAll_UnknownCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'SomeCollection' not found. Create collection first");
    jsonDBTemplate.findAll("SomeCollection");
  }

  /**
   * test to find a single document from the complete collection.
   */
  @Test
  public void testFindOne_Document() {
    String jxQuery = "."; //XPATH for all elements in a collection
    Instance instance = jsonDBTemplate.findOne(jxQuery, Instance.class);
    assertNotNull(instance);
  }

  /**
   * test to find a single document for a query that returns only one document.
   */
  @Test
  public void testFindOne_SingleDocumentFromOne() {
    String jxQuery = String.format("/.[id='%s']", "01");
    Instance instance = jsonDBTemplate.findOne(jxQuery, Instance.class);
    assertNotNull(instance);
    assertEquals(instance.getId(), "01");
  }

  /**
   * test to find a single document for a query that can returns more than one document.
   */
  @Test
  public void testFindOne_SingleDocumentFromMany() {
    String jxQuery = String.format("/.[publicKey='%s']", "d3aa045f71bf4d1dffd2c5f485a4bc1d");
    Instance instance = jsonDBTemplate.findOne(jxQuery, Instance.class);
    assertNotNull(instance);
  }

  /**
   * test to find a document for Entity type which does not have @Document annotation
   */
  @Test
  public void testFindOne_NonAnotatedClass() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Entity 'NonAnotatedClass' is not annotated with annotation @Document");
    jsonDBTemplate.findOne("000000", NonAnotatedClass.class);
  }

  /**
   * test to find a document for a unknown collection name
   */
  @Test
  public void testFindOne_UnknownCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'SomeCollection' not found. Create collection first");
    jsonDBTemplate.findOne("000000", "SomeCollection");
  }

  /**
   * test to find a single document with a non-existent id.
   */
  @Test
  public void testFindOne_NonExistentId() {
    String jxQuery = String.format("/.[id='%s']", "000000");
    Instance instance = jsonDBTemplate.findOne(jxQuery, Instance.class);
    assertNull(instance);
  }

  /**
   * a test that demonstrates how to query for a attribute with null value
   */
  @Test
  public void testFindQuery_DocumentWithNullAttribute() {
    String jxQuery = "/.[publicKey='']";
    Instance c = jsonDBTemplate.findOne(jxQuery, Instance.class);
    assertNotNull(c);
    assertEquals(c.getId(), "06");
  }

  /**
   * a test that demonstrates how to query for a attribute that is not null.
   */
  @Test
  public void testFindQuery_ForDocumentWithAttributeNotNull() {
    String jxQuery = "/.[not(publicKey='')]";
    List<Instance> instances = jsonDBTemplate.find(jxQuery, Instance.class);
    assertNotNull(instances);
    assertEquals(instances.size(), 5);
    for (Instance c : instances) {
      assertNotEquals(c.getId(), "06");
    }
  }
}

