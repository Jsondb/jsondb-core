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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;

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

/**
 * Unit tests that cover all aspects of DB initialization
 *
 * @author Farooq Khan
 * @version 1.0 06-Oct-2016
 */
public class InitializeDBNegativeTests {

  private String dbFilesLocation = "src/test/resources/dbfiles/dbInitializationTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");
  
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() throws IOException, GeneralSecurityException {
    dbFilesFolder.mkdir();
  }

  @After
  public void tearDown() {
    Util.delete(dbFilesFolder);
  }

  /**
   * A test to see if verify if JsonDB will get initialized when emtpy directory is passed.
   */
  @Test
  public void testEmptyDBInitialization() {
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");

    Set<String> collectionNames = jsonDBTemplate.getCollectionNames();
    assertEquals(collectionNames.size(), 0);
  }

  /**
   * A test to see if JsonDB will initialize when a non existing directory is passed
   */
  @Test
  public void testMissingDBLocationInitialization_1() {
    File someDbFilesFolder = new File(dbFilesLocation,  "someMissingFolder");
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(someDbFilesFolder.toString(), "org.jsondb.testmodel");

    assertTrue(someDbFilesFolder.exists());
    assertTrue(someDbFilesFolder.isDirectory());

    Set<String> collectionNames = jsonDBTemplate.getCollectionNames();
    assertEquals(collectionNames.size(), 0);
  }

  /**
   * A test to see if JsonDB will throw exception when passing non-creatable a non existing directory is passed
   */
  @Test
  public void testMissingDBLocationInitialization_2() {
    File someDbFilesFolder = new File(dbFilesLocation,  "someMissingFolder2");
    try {
      someDbFilesFolder.createNewFile();
    } catch (IOException e) {
      fail("Failed while creating temporary folder " + someDbFilesFolder.toString());
    }

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Specified DbFiles directory is actually a file cannot use it as a directory");

    new JsonDBTemplate(someDbFilesFolder.toString(), "org.jsondb.tests.model");
  }
  
  @Test
  public void testDBInitializationforMissingFile() throws IOException, GeneralSecurityException {
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    ICipher cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher);
    
    assertTrue(jsonDBTemplate.collectionExists("instances"));
    
    instancesJson.delete();
    jsonDBTemplate.reloadCollection("instances");
    assertTrue(!jsonDBTemplate.collectionExists("instances"));
  }
}
