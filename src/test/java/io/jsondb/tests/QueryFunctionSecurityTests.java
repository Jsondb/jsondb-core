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
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.List;

import org.apache.commons.jxpath.JXPathException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;

/**
 * Queries must not be able to call arbitrary Java methods or constructors through
 * JXPath extension functions, since query strings often carry user input.
 */
public class QueryFunctionSecurityTests {
  private static final String PROBE_PROPERTY = "jsondb.tests.queryFunctionProbe";

  private String dbFilesLocation = "src/test/resources/dbfiles/queryFunctionSecurityTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    System.clearProperty(PROBE_PROPERTY);
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
  }

  @After
  public void tearDown() throws Exception {
    System.clearProperty(PROBE_PROPERTY);
    Util.delete(dbFilesFolder);
  }

  private String staticMethodCallQuery() {
    return "/.[java.lang.System.setProperty('" + PROBE_PROPERTY + "','called')]";
  }

  @Test
  public void testFind_StaticMethodCallIsRejected() {
    try {
      jsonDBTemplate.find(staticMethodCallQuery(), Instance.class);
    } catch (JXPathException expected) {
      // Rejecting the query is fine, running it is not
    }
    assertNull(System.getProperty(PROBE_PROPERTY));
  }

  @Test
  public void testFindOne_StaticMethodCallIsRejected() {
    try {
      jsonDBTemplate.findOne(staticMethodCallQuery(), Instance.class);
    } catch (JXPathException expected) {
      // Rejecting the query is fine, running it is not
    }
    assertNull(System.getProperty(PROBE_PROPERTY));
  }

  @Test(expected = JXPathException.class)
  public void testFind_ConstructorCallIsRejected() {
    jsonDBTemplate.find("/.[java.lang.StringBuilder.new('x')]", Instance.class);
  }

  @Test
  public void testFind_StaticMethodCallIsRejectedOnNewlyCreatedCollection() {
    jsonDBTemplate.createCollection(Site.class);
    Site site = new Site();
    site.setId("s1");
    jsonDBTemplate.insert(site);
    try {
      jsonDBTemplate.find(staticMethodCallQuery(), Site.class);
    } catch (JXPathException expected) {
      // Rejecting the query is fine, running it is not
    }
    assertNull(System.getProperty(PROBE_PROPERTY));
  }

  @Test
  public void testFind_CoreXPathFunctionsStillWork() {
    List<Instance> instances = jsonDBTemplate.find("/.[contains(hostname,'191-0') and string-length(publicKey)=0]", Instance.class);
    assertEquals(1, instances.size());
    assertEquals("06", instances.get(0).getId());
  }
}
