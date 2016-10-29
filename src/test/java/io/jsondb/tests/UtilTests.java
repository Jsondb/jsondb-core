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

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.PojoForPrivateGetIdTest;
import io.jsondb.tests.model.PojoForPrivateSetIdTest;

/**
 * A unit test class for methods in Util class
 * @version 1.0 27-Oct-2016
 */
public class UtilTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/utilTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  
  private JsonDBTemplate jsonDBTemplate = null;
  
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  
  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
  }
  
  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  @Test
  public void test_determineCollectionName() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("No class parameter provided, entity collection can't be determined");
    jsonDBTemplate.getCollectionName(null);
  }
  
  @Test
  public void test_getIdForEntity_1() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Failed to invoke getter method for a idAnnotated field due to permissions");
    
    jsonDBTemplate.createCollection(PojoForPrivateGetIdTest.class);
    
    PojoForPrivateGetIdTest s = new PojoForPrivateGetIdTest("001");
    jsonDBTemplate.insert(s);
  }
  
  @Test
  public void test_setIdForEntity_1() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Failed to invoke setter method for a idAnnotated field due to permissions");
    
    jsonDBTemplate.createCollection(PojoForPrivateSetIdTest.class);
    
    PojoForPrivateSetIdTest s = new PojoForPrivateSetIdTest();
    jsonDBTemplate.insert(s);
  }
}
