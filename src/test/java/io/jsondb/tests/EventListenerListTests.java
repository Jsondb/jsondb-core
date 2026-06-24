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
