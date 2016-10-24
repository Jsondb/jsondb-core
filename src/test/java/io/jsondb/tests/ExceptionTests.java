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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBException;
import io.jsondb.io.JsonFileLockException;

/**
 * Dumb unit tests for some of the Exception classes
 * @version 1.0 25-Oct-2016
 */
public class ExceptionTests {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  
  @Test
  public void testJsonfileLockException() throws JsonFileLockException {
    expectedException.expect(JsonFileLockException.class);
    expectedException.expectMessage("JsonReader failed to obtain a file lock for file");
   
    throw new JsonFileLockException("JsonReader failed to obtain a file lock for file", null);
  }
  
  @Test
  public void testJsonDBException1() throws JsonDBException {
    expectedException.expect(JsonDBException.class);
    expectedException.expectMessage("Some Exception");
   
    throw new JsonDBException("Some Exception");
  }
  
  @Test
  public void testJsonDBException2() throws JsonDBException {
    expectedException.expect(JsonDBException.class);
    expectedException.expectMessage("Some Exception");
   
    throw new JsonDBException("Some Exception", null);
  }
  
  @Test
  public void testInvalidJsonDbApiUsageException() throws InvalidJsonDbApiUsageException {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Some Invalid usage");
   
    throw new InvalidJsonDbApiUsageException("Some Invalid usage", null);
  }
}
