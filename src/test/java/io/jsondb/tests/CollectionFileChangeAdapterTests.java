package io.jsondb.tests;

import org.junit.Test;

import io.jsondb.events.CollectionFileChangeAdapter;

public class CollectionFileChangeAdapterTests {

  @Test
  public void testDefaultAdapterMethodsAreNoOp() {
    CollectionFileChangeAdapter adapter = new CollectionFileChangeAdapter() {
    };
    adapter.collectionFileAdded("instances");
    adapter.collectionFileModified("instances");
    adapter.collectionFileDeleted("instances");
  }
}
