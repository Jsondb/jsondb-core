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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.JXPathIntrospector;
import org.junit.BeforeClass;
import org.junit.Test;

import io.jsondb.ReflectionPropertyHandler;
import io.jsondb.tests.querypath.HandlerShapes;

/**
 * Unit tests for ReflectionPropertyHandler on its own. QueryPropertyHandlerTests covers it end to end.
 */
public class ReflectionPropertyHandlerTests {
  private final ReflectionPropertyHandler handler = new ReflectionPropertyHandler();

  @BeforeClass
  public static void registerShapes() {
    ReflectionPropertyHandler.register(HandlerShapes.Root.class);
  }

  @Test
  public void testJavaBeansIsAvailableOnTheJvm() {
    assertTrue(ReflectionPropertyHandler.isJavaBeansAvailable());
  }

  @Test
  public void testRegisterIfJavaBeansMissingDoesNothingOnTheJvm() {
    ReflectionPropertyHandler.registerIfJavaBeansMissing(HandlerShapes.Throwing.class);
    assertFalse(JXPathIntrospector.getBeanInfo(HandlerShapes.Throwing.class).isDynamic());
  }

  @Test
  public void testRegistersClassesReachedThroughArraysMapsAndWildcards() {
    assertTrue(JXPathIntrospector.getBeanInfo(HandlerShapes.Root.class).isDynamic());
    assertTrue(JXPathIntrospector.getBeanInfo(HandlerShapes.ArrayItem.class).isDynamic());
    assertTrue(JXPathIntrospector.getBeanInfo(HandlerShapes.MapValue.class).isDynamic());
    assertTrue(JXPathIntrospector.getBeanInfo(HandlerShapes.WildcardItem.class).isDynamic());
  }

  @Test
  public void testRegistersEnumsAndJdkTypesAsAtomicValues() {
    assertTrue(JXPathIntrospector.getBeanInfo(HandlerShapes.Kind.class).isAtomic());
    assertTrue(JXPathIntrospector.getBeanInfo(java.time.LocalDate.class).isAtomic());
  }

  @Test
  public void testPropertyNamesFollowJavaBeansRulesAndOmitClass() {
    assertArrayEquals(new String[] {"URL", "array", "date", "kind", "map", "readOnly", "wildcard"},
        handler.getPropertyNames(new HandlerShapes.Root()));
  }

  @Test
  public void testGetAndSetProperty() {
    HandlerShapes.Root root = new HandlerShapes.Root();
    handler.setProperty(root, "URL", "http://jsondb.io");
    assertEquals("http://jsondb.io", root.getURL());
    assertEquals("http://jsondb.io", handler.getProperty(root, "URL"));
  }

  @Test
  public void testGetUnknownPropertyReturnsNull() {
    assertNull(handler.getProperty(new HandlerShapes.Root(), "nosuch"));
  }

  @Test(expected = JXPathException.class)
  public void testSetReadOnlyPropertyFails() {
    handler.setProperty(new HandlerShapes.Root(), "readOnly", "changed");
  }

  @Test(expected = JXPathException.class)
  public void testGetterExceptionIsWrapped() {
    handler.getProperty(new HandlerShapes.Throwing(), "broken");
  }

  @Test(expected = JXPathException.class)
  public void testSetterExceptionIsWrapped() {
    handler.setProperty(new HandlerShapes.Throwing(), "broken", "x");
  }
}
