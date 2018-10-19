/*
 * Copyright (c) 2018 Farooq Khan
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
import java.io.IOException;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.util.TestUtils;

/**
 * @version 1.0 19-Oct-2018
 */
public class LoadInvalidJsonTests {

  private String dbFilesLocation = "src/test/resources/dbfiles/loadInvalidJsonTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");
  
  @Before
  public void setup() throws IOException {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
  }

  @After
  public void tearDown() {
    Util.delete(dbFilesFolder);
  }

  /**
   * A test to ensure JsonDB does not delete the source files when a exception occurs during loading
   */
  @Test
  public void testLoadForInvalidJson() {
    // The invalidJson has semicolon instead of a colon between the id attribute name and value
    String invalidJson = "{\"id\"=\"07\",\"hostname\":\"ec2-54-191-07\",\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\",\"publicKey\":\"\"}";
    
    TestUtils.appendDirectToFile(instancesJson, invalidJson);
    
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    Set<String> collectionNames = jsonDBTemplate.getCollectionNames();
    
    assertEquals(collectionNames.size(), 0);
    assertEquals(8, TestUtils.getNoOfLinesInFile(instancesJson));
  }
}
