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
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.PojoWithEnumFields;
import io.jsondb.tests.model.PojoWithEnumFields.Status;

/**
 * @author Farooq Khan
 * @version 1.0 06-Oct-2016
 */
public class PojoWithEnumFieldsTest {
  private String dbFilesLocation = "src/test/resources/dbfiles/pojowithenumfieldsTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File pojoWithEnumFieldsJson = new File(dbFilesFolder, "pojowithenumfields.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/pojowithenumfields.json"), pojoWithEnumFieldsJson);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  @Test
  public void testFind() {
    PojoWithEnumFields clazz = jsonDBTemplate.findById("0001", PojoWithEnumFields.class);

    assertNotNull(clazz);

    assertEquals(clazz.getStatus(), Status.CREATED);
  }

  @Test
  public void testInsert() {
    List<PojoWithEnumFields> clazzs = jsonDBTemplate.getCollection(PojoWithEnumFields.class);
    int size = clazzs.size();

    PojoWithEnumFields clazz = new PojoWithEnumFields();
    clazz.setId("0010");
    clazz.setStatus(Status.UPDATED);
    jsonDBTemplate.insert(clazz);

    clazzs = jsonDBTemplate.getCollection(PojoWithEnumFields.class);
    assertNotNull(clazzs);
    assertEquals(clazzs.size(), size+1);
  }
}
