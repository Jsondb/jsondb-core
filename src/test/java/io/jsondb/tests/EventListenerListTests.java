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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.CollectionMetaData;
import io.jsondb.JsonDBConfig;
import io.jsondb.Util;
import io.jsondb.events.CollectionFileChangeListener;
import io.jsondb.events.EventListenerList;

public class EventListenerListTests {

  private static final String DB = "src/test/resources/dbfiles/eventListenerListTests";
  private File dbFolder = new File(DB);

  @Before
  public void setUp() {
    dbFolder.mkdirs();
  }

  @After
  public void tearDown() {
    Util.delete(dbFolder);
  }

  @Test
  public void testListenerLifecycle() {
    JsonDBConfig dbConfig = new JsonDBConfig(DB, "io.jsondb.tests.model", null, false, null);
    Map<String, CollectionMetaData> cmdMap = CollectionMetaData.builder(dbConfig);
    EventListenerList listenerList = new EventListenerList(dbConfig, cmdMap);

    CollectionFileChangeListener first = new CollectionFileChangeListener() {
      @Override
      public void collectionFileAdded(String collectionName) {
      }

      @Override
      public void collectionFileModified(String collectionName) {
      }

      @Override
      public void collectionFileDeleted(String collectionName) {
      }
    };
    CollectionFileChangeListener second = new CollectionFileChangeListener() {
      @Override
      public void collectionFileAdded(String collectionName) {
      }

      @Override
      public void collectionFileModified(String collectionName) {
      }

      @Override
      public void collectionFileDeleted(String collectionName) {
      }
    };

    assertFalse(listenerList.hasCollectionFileChangeListener());

    listenerList.addCollectionFileChangeListener(first);
    assertTrue(listenerList.hasCollectionFileChangeListener());

    listenerList.addCollectionFileChangeListener(second);
    assertTrue(listenerList.hasCollectionFileChangeListener());

    listenerList.removeCollectionFileChangeListener(first);
    assertTrue(listenerList.hasCollectionFileChangeListener());

    listenerList.removeCollectionFileChangeListener(second);
    assertFalse(listenerList.hasCollectionFileChangeListener());
  }

  @Test
  public void testShutdownClearsListeners() {
    JsonDBConfig dbConfig = new JsonDBConfig(DB, "io.jsondb.tests.model", null, false, null);
    Map<String, CollectionMetaData> cmdMap = CollectionMetaData.builder(dbConfig);
    EventListenerList listenerList = new EventListenerList(dbConfig, cmdMap);

    listenerList.addCollectionFileChangeListener(new CollectionFileChangeListener() {
      @Override
      public void collectionFileAdded(String collectionName) {
      }

      @Override
      public void collectionFileModified(String collectionName) {
      }

      @Override
      public void collectionFileDeleted(String collectionName) {
      }
    });

    listenerList.shutdown();
    assertFalse(listenerList.hasCollectionFileChangeListener());
  }
}
