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
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.util.TestUtils;

public class LoadJsonMappingTests {

  private static final String DB = "src/test/resources/dbfiles/loadJsonMappingTests";
  private File dbFolder = new File(DB);
  private File instancesJson = new File(dbFolder, "instances.json");

  @Before
  public void setUp() throws IOException {
    dbFolder.mkdirs();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
  }

  @After
  public void tearDown() {
    Util.delete(dbFolder);
  }

  @Test
  public void testLoadForInvalidMappingLeavesSourceFileIntact() {
    String invalidMappingJson =
        "{\"id\":\"07\",\"hostname\":{\"not\":\"a-string\"},\"privateKey\":\"secret\",\"publicKey\":\"pub\"}";
    TestUtils.appendDirectToFile(instancesJson, invalidMappingJson);

    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");

    assertEquals(0, template.getCollectionNames().size());
    assertFalse(template.collectionExists("instances"));
    assertEquals(8, TestUtils.getNoOfLinesInFile(instancesJson));
  }
}
