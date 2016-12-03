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
import java.util.HashSet;
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
import io.jsondb.tests.model.Volume;
import io.jsondb.tests.util.TestUtils;

/**
 * @version 1.0 08-Oct-2016
 */
public class UpsertTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/upsertTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");
  private File volumesJson = new File(dbFilesFolder, "volumes.json");

  private JsonDBTemplate jsonDBTemplate = null;
  private ICipher cipher;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  /**
   * Test to upsert a new object into a known collection type which has no data.
   */
  @Test
  public void testUpsert_NewObjectCollectionWithNoData() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    jsonDBTemplate.upsert(new Site());
  }

  /**
   * Test to upsert a new object into a known collection type which has some data.
   */
  @Test
  public void testUpsert_NewObjectCollectionWithSomeData() {
    List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
    int size = instances.size();

    Instance instance = new Instance();
    instance.setId("07");
    instance.setHostname("ec2-54-191-07");
    instance.setPrivateKey("PrivateRyanSaved");
    instance.setPublicKey("TomHanks");
    jsonDBTemplate.upsert(instance);

    instances = jsonDBTemplate.getCollection(Instance.class);
    assertNotNull(instances);
    assertEquals(size+1, instances.size());
  }

  private class SomeClass {}

  /**
   * Test to upsert a new object of unknown collection type.
   */
  @Test
  public void testUpsert_NewObjectUnknownCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Entity 'SomeClass' is not annotated with annotation @Document");
    jsonDBTemplate.upsert(new SomeClass());
  }

  /**
   * Test to upsert a null object.
   */
  @Test
  public void testUpsert_Null_1() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Null Object cannot be upserted into DB");
    jsonDBTemplate.upsert(null);
  }
  
  /**
   * Test to upsert a null object.
   */
  @Test
  public void testUpsert_Null_2() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Null Object cannot be upserted into DB");
    Object nullObject = null;
    jsonDBTemplate.upsert(nullObject, "instances");
  }
  
  /**
   * Test to upsert a null object.
   */
  @Test
  public void testUpsert_Null_3() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Null Object batch cannot be upserted into DB");
    jsonDBTemplate.upsert(null, "instances");
  }

  /**
   * Test to upsert a Collection object.
   */
  @Test
  public void testUpsert_CollectionObject() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection object cannot be inserted, removed, updated or upserted as a single object");
    jsonDBTemplate.upsert(new HashSet<String>());
  }

  /**
   * Test to upsert a new object with a ID that already exists..
   */
  @Test
  public void testUpsert_ObjectWithExistingId() {
    Instance instance1 = jsonDBTemplate.findById("03", Instance.class);
    assertNotNull(instance1);
    assertEquals("03", instance1.getId());
    assertEquals("ec2-54-191-03", instance1.getHostname());
    assertEquals("b87eb02f5dd7e5232d7b0fc30a5015e4", instance1.getPrivateKey());
    assertEquals("d3aa045f71bf4d1dffd2c5f485a4bc1d", instance1.getPublicKey());

    Instance instance2 = new Instance();
    instance2.setId("03");
    instance2.setHostname("ec2-54-191-03_Updated");
    instance2.setPrivateKey("SavingPrivateRyan");
    instance2.setPublicKey("VeryPublic");
    jsonDBTemplate.upsert(instance2);

    Instance instance3 = jsonDBTemplate.findById("03", Instance.class);
    assertNotNull(instance3);
    assertEquals("03", instance3.getId());
    assertEquals("ec2-54-191-03_Updated", instance3.getHostname());
    assertEquals("SavingPrivateRyan", instance3.getPrivateKey());
    assertEquals("VeryPublic", instance3.getPublicKey());
  }

  /**
   * Test to upsert a new object into a collection and verify the actual file output.
   */
  @Test
  public void testUpsert_InsertAndVerify() {
    List<Volume> vols = jsonDBTemplate.getCollection(Volume.class);
    int size = vols.size();

    Volume vol = new Volume();
    vol.setId("000001");
    vol.setName("c:");
    vol.setSize(102400000000L);
    vol.setFlash(true);
    jsonDBTemplate.upsert(vol);

    vols = jsonDBTemplate.getCollection(Volume.class);
    assertNotNull(vols);
    assertEquals(size+1, vols.size());

    String[] expectedLinesAtEnd = {"{\"id\":\"000001\",\"name\":\"c:\",\"size\":102400000000,\"flash\":true}"};

    TestUtils.checkLastLines(volumesJson, expectedLinesAtEnd);
  }

  /**
   * Test to upsert a collection of objects.
   */
  @Test
  public void testUpsert_CollectionOfObjects() {
    List<Volume> vols = jsonDBTemplate.getCollection(Volume.class);
    int size = vols.size();

    List<Volume> newList = new ArrayList<Volume>();
    for (int i=0; i<5; i++) {
      Volume vol = new Volume();
      int id = 2 + i;
      vol.setId(String.format("%06d", id));
      vol.setName("c:");
      vol.setSize(10240000L * i);
      vol.setFlash((i%2==0));
      newList.add(vol);
    }
    jsonDBTemplate.upsert(newList, Volume.class);

    vols = jsonDBTemplate.getCollection(Volume.class);
    assertNotNull(vols);
    assertEquals(size+5, vols.size());

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"000002\",\"name\":\"c:\",\"size\":0,\"flash\":true}",
        "{\"id\":\"000003\",\"name\":\"c:\",\"size\":10240000,\"flash\":false}",
        "{\"id\":\"000004\",\"name\":\"c:\",\"size\":20480000,\"flash\":true}",
        "{\"id\":\"000005\",\"name\":\"c:\",\"size\":30720000,\"flash\":false}",
        "{\"id\":\"000006\",\"name\":\"c:\",\"size\":40960000,\"flash\":true}"};

    TestUtils.checkLastLines(volumesJson, expectedLinesAtEnd);
  }

  /**
   * Test to upsert a collection of objects with one of them already present in DB.
   */
  @Test
  public void testUpsert_CollectionOfObjectsWithOnePresent() {
    List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
    int size = instances.size();

    List<Instance> newList = new ArrayList<Instance>();
    for (int i=0; i<5; i++) {
      Instance c = new Instance();
      int id = 21 + i;
      c.setId(String.format("%02d", id));
      newList.add(c);
    }
    Instance instance = new Instance();
    instance.setId("03");
    newList.add(instance);

    jsonDBTemplate.upsert(newList, Instance.class);

    instances = jsonDBTemplate.getCollection(Instance.class);
    assertNotNull(instances);
    assertEquals(size+5, instances.size());
  }

  /**
   * Test to upsert a collection of objects with duplicate.
   */
  @Test
  public void testUpsert_CollectionOfObjectsWithDuplicate() {
    List<Instance> newList = new ArrayList<Instance>();
    for (int i=0; i<2; i++) {
      Instance c = new Instance();
      c.setId("000028");
      newList.add(c);
    }

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Duplicate object with id: 000028 within the passed in parameter");
    jsonDBTemplate.upsert(newList, Instance.class);
  }

  /**
   * Test to upsert a collection of objects of unknown type into DB.
   */
  @Test
  public void testUpsert_ObjectsOfUnknownType() {
    List<Site> newList = new ArrayList<Site>();
    for (int i=0; i<5; i++) {
      Site c = new Site();
      int id = 0 + i;
      c.setId(String.format("%06d", id));
      newList.add(c);
    }

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    jsonDBTemplate.upsert(newList, Site.class);
  }

  /**
   * Test to upsert objects without a ID set.
   */
  @Test
  public void testInsert_ObjectWithoutId() {
    List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
    int size = instances.size();

    Instance instance = new Instance();
    instance.setHostname("VplexServer");
    instance.setPrivateKey("PrivateNetwork");
    instance.setPublicKey("PublicNetwork");
    jsonDBTemplate.upsert(instance);

    instances = jsonDBTemplate.getCollection(Instance.class);
    assertNotNull(instances);
    assertEquals(size+1, instances.size());
  }
}
