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

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;

/**
 * count() returns a collection's size without copying its documents, as findAll() does (issue #54).
 */
public class CollectionCountTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/collectionCountTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  @Test
  public void testCountByClassAndByName() {
    assertEquals(6, jsonDBTemplate.count(Instance.class));
    assertEquals(6, jsonDBTemplate.count("instances"));
  }

  @Test
  public void testCountFollowsInsertsAndRemoves() {
    Instance instance = new Instance();
    instance.setId("07");
    jsonDBTemplate.insert(instance);
    assertEquals(7, jsonDBTemplate.count(Instance.class));

    jsonDBTemplate.remove(jsonDBTemplate.findById("01", Instance.class), Instance.class);
    assertEquals(6, jsonDBTemplate.count(Instance.class));
  }

  @Test
  public void testCountOfEmptyCollection() {
    jsonDBTemplate.createCollection(Site.class);
    assertEquals(0, jsonDBTemplate.count(Site.class));
  }

  @Test
  public void testCountOfMissingCollectionIsRejected() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first.");
    jsonDBTemplate.count(Site.class);
  }
}
