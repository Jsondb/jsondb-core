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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.jxpath.JXPathIntrospector;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsondb.JsonDBTemplate;
import io.jsondb.ReflectionPropertyHandler;
import io.jsondb.Util;
import io.jsondb.tests.querypath.BeanPathDoc;
import io.jsondb.tests.querypath.DynamicPathDoc;

/**
 * Android has no java.beans, which JXPath normally uses to read document properties. There JsonDB
 * registers document classes with ReflectionPropertyHandler instead. java.beans is always present
 * on the JVM, so these tests force the handler onto DynamicPathDoc and check every query returns
 * the same documents as the identical BeanPathDoc does through the normal java.beans path.
 */
public class QueryPropertyHandlerTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/queryPropertyHandlerTests";
  private File dbFilesFolder = new File(dbFilesLocation);

  private JsonDBTemplate jsonDBTemplate = null;

  @BeforeClass
  public static void registerHandler() {
    ReflectionPropertyHandler.register(DynamicPathDoc.class);
  }

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.querypath");
    jsonDBTemplate.createCollection(BeanPathDoc.class);
    jsonDBTemplate.createCollection(DynamicPathDoc.class);

    List<BeanPathDoc> docs = Arrays.asList(
        doc("1", "a", 8080, true, BeanPathDoc.Status.ACTIVE, "Pune", Arrays.asList("x", "y")),
        doc("2", "b", 80, false, BeanPathDoc.Status.RETIRED, "Mumbai", Arrays.asList("y")),
        doc("3", null, 9000, true, BeanPathDoc.Status.ACTIVE, null, null),
        doc("4", "c", 443, false, BeanPathDoc.Status.ACTIVE, "Pune", Collections.<String>emptyList()));
    ObjectMapper mapper = new ObjectMapper();
    List<DynamicPathDoc> dynamicDocs = new ArrayList<DynamicPathDoc>();
    for (BeanPathDoc d : docs) {
      dynamicDocs.add(mapper.convertValue(d, DynamicPathDoc.class));
    }
    jsonDBTemplate.insert(docs, BeanPathDoc.class);
    jsonDBTemplate.insert(dynamicDocs, DynamicPathDoc.class);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  private static BeanPathDoc doc(String id, String hostname, int port, boolean active, BeanPathDoc.Status status,
      String city, List<String> tags) {
    BeanPathDoc d = new BeanPathDoc();
    d.setId(id);
    d.setHostname(hostname);
    d.setPort(port);
    d.setActive(active);
    d.setStatus(status);
    if (null != city) {
      BeanPathDoc.Address address = new BeanPathDoc.Address();
      address.setCity(city);
      d.setAddress(address);
    }
    d.setTags(tags);
    return d;
  }

  private List<String> beanPathIds(String jxQuery) {
    List<String> ids = new ArrayList<String>();
    for (BeanPathDoc d : jsonDBTemplate.find(jxQuery, BeanPathDoc.class)) {
      ids.add(d.getId());
    }
    Collections.sort(ids);
    return ids;
  }

  private List<String> dynamicPathIds(String jxQuery) {
    List<String> ids = new ArrayList<String>();
    for (DynamicPathDoc d : jsonDBTemplate.find(jxQuery, DynamicPathDoc.class)) {
      ids.add(d.getId());
    }
    Collections.sort(ids);
    return ids;
  }

  private void assertBothPaths(String jxQuery, String... expectedIds) {
    List<String> expected = Arrays.asList(expectedIds);
    assertEquals("java.beans path for " + jxQuery, expected, beanPathIds(jxQuery));
    assertEquals("handler path for " + jxQuery, expected, dynamicPathIds(jxQuery));
  }

  private void assertPathsAgree(String jxQuery) {
    assertEquals("handler path differs from java.beans path for " + jxQuery, beanPathIds(jxQuery), dynamicPathIds(jxQuery));
  }

  @Test
  public void testHandlerIsUsedOnlyForRegisteredClasses() {
    assertTrue(JXPathIntrospector.getBeanInfo(DynamicPathDoc.class).isDynamic());
    assertTrue(JXPathIntrospector.getBeanInfo(DynamicPathDoc.Address.class).isDynamic());
    assertFalse(JXPathIntrospector.getBeanInfo(BeanPathDoc.class).isDynamic());
    assertFalse(JXPathIntrospector.getBeanInfo(BeanPathDoc.Address.class).isDynamic());
  }

  @Test
  public void testStringProperty() {
    assertBothPaths("/.[hostname='a']", "1");
  }

  @Test
  public void testIntProperty() {
    assertBothPaths("/.[port>1000]", "1", "3");
  }

  @Test
  public void testBooleanIsGetterProperty() {
    assertBothPaths("/.[active=true()]", "1", "3");
  }

  @Test
  public void testEnumProperty() {
    assertBothPaths("/.[status='ACTIVE']", "1", "3", "4");
  }

  @Test
  public void testNestedObjectProperty() {
    assertBothPaths("/.[address/city='Pune']", "1", "4");
  }

  @Test
  public void testListProperty() {
    assertBothPaths("/.[tags='y']", "1", "2");
  }

  @Test
  public void testCombinedConditions() {
    assertBothPaths("/.[active=true() and address/city='Pune']", "1");
  }

  @Test
  public void testNullPropertiesBehaveTheSame() {
    assertPathsAgree("/.[not(hostname='a')]");
    assertPathsAgree("/.[not(address/city='Pune')]");
    assertPathsAgree("/.[count(tags)=0]");
    assertPathsAgree("/.[hostname]");
  }

  @Test
  public void testFindOneAndFindAll() {
    assertEquals("2", jsonDBTemplate.findOne("/.[hostname='b']", DynamicPathDoc.class).getId());
    assertEquals(4, jsonDBTemplate.findAll(DynamicPathDoc.class).size());
  }
}
