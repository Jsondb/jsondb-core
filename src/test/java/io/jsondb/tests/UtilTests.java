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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;

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
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.testmodel");
  }
  @Test
  public void testDetermineCollectionName() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("No class parameter provided, entity collection can't be determined");
    jsonDBTemplate.getCollectionName(null);
  }
}
