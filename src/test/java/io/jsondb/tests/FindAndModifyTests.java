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

import java.io.File;
import java.util.List;
import java.util.Map;

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
import io.jsondb.query.Update;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;

/**
 * @version 1.0 15-Oct-2016
 */
public class FindAndModifyTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/findAndModifyTests";
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

  @Test
  public void testFindAndModify_1() {
    Update update = new Update();
    update.set("privateKey", "SavingPrivateRyan");

    String jxQuery = String.format("/.[id='%s']", "01");
    jsonDBTemplate.findAndModify(jxQuery, update, "instances");

    Instance instance = jsonDBTemplate.findById("01", "instances");
    assertEquals("SavingPrivateRyan", instance.getPrivateKey());
  }

  @Test
  public void testFindAndModify_2() {
    Update update = Update.update("publicKey", "Its all Public");

    String jxQuery = String.format("/.[id='%s']", "02");
    jsonDBTemplate.findAndModify(jxQuery, update, "instances");

    Instance instance = jsonDBTemplate.findById("02", "instances");
    assertEquals("Its all Public", instance.getPublicKey());
  }

  @Test
  public void testFindAndModify_3() {
    Update update = Update.update("SomeColumn1", "Value 1");
    update.set("SomeColumn2", "Value 2");

    Map<String, Object> allUpdates = update.getUpdateData();
    assertEquals(2, allUpdates.size());

    assertEquals("Value 1", allUpdates.get("SomeColumn1"));
    assertEquals("Value 2", allUpdates.get("SomeColumn2"));
  }

  @Test
  public void testFindAndModify_4() {
    Update update = Update.update("privateKey", "SavingPrivateRyan");
    update.set("publicKey", "SavedByPublic");

    String jxQuery = String.format("/.[id='%s']", "03");
    Instance retInstance = jsonDBTemplate.findAndModify(jxQuery, update, "instances");
    assertEquals("SavingPrivateRyan", retInstance.getPrivateKey());
    assertEquals("SavedByPublic", retInstance.getPublicKey());

    Instance instance = jsonDBTemplate.findById("03", "instances");
    assertEquals("SavingPrivateRyan", instance.getPrivateKey());
    assertEquals("SavedByPublic", instance.getPublicKey());
  }

  @Test
  public void testFindAndModify_NonExistingCollection_1() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    String jxQuery = String.format("/.[id>'%s']", "03");
    jsonDBTemplate.findAndModify(jxQuery, null, "sites");
  }
  
  @Test
  public void testFindAndModify_NonExistingCollection_2() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    String jxQuery = String.format("/.[id>'%s']", "03");
    
    jsonDBTemplate.findAndModify(jxQuery, null, Site.class);
  }
  
  @Test
  public void testFindAllAndModify() {
    Update update = Update.update("privateKey", "SavingPrivateRyan");
    update.set("publicKey", "SavedByPublic");

    String jxQuery = String.format("/.[id>'%s']", "03");
    List<Instance> retInstances = jsonDBTemplate.findAllAndModify(jxQuery, update, "instances");
    assertEquals(3, retInstances.size());
    assertEquals("SavingPrivateRyan", retInstances.get(0).getPrivateKey());
    assertEquals("SavedByPublic", retInstances.get(0).getPublicKey());
    assertEquals("SavingPrivateRyan", retInstances.get(1).getPrivateKey());
    assertEquals("SavedByPublic", retInstances.get(1).getPublicKey());
    assertEquals("SavingPrivateRyan", retInstances.get(2).getPrivateKey());
    assertEquals("SavedByPublic", retInstances.get(2).getPublicKey());

    Instance instance1 = jsonDBTemplate.findById("04", "instances");
    assertEquals("SavingPrivateRyan", instance1.getPrivateKey());
    assertEquals("SavedByPublic", instance1.getPublicKey());

    Instance instance2 = jsonDBTemplate.findById("05", "instances");
    assertEquals("SavingPrivateRyan", instance2.getPrivateKey());
    assertEquals("SavedByPublic", instance2.getPublicKey());

    Instance instance3 = jsonDBTemplate.findById("06", "instances");
    assertEquals("SavingPrivateRyan", instance3.getPrivateKey());
    assertEquals("SavedByPublic", instance3.getPublicKey());
  }
  
  @Test
  public void testFindAllAndModify_NonExistingCollection_1() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    String jxQuery = String.format("/.[id>'%s']", "03");
    jsonDBTemplate.findAllAndModify(jxQuery, null, "sites");
  }
  
  @Test
  public void testFindAllAndModify_NonExistingCollection_2() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    String jxQuery = String.format("/.[id>'%s']", "03");
    jsonDBTemplate.findAllAndModify(jxQuery, null, Site.class);
  }
}
