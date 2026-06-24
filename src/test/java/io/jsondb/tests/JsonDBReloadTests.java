package io.jsondb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.events.CollectionFileChangeAdapter;
import io.jsondb.tests.model.Instance;

public class JsonDBReloadTests {

  private static final String DB = "src/test/resources/dbfiles/jsonDbReloadTests";
  private File dbFolder = new File(DB);
  private File instancesJson = new File(dbFolder, "instances.json");

  @Before
  public void setUp() throws Exception {
    dbFolder.mkdirs();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
  }

  @After
  public void tearDown() {
    Util.delete(dbFolder);
  }

  @Test
  public void testReLoadDBRemovesCollectionWhenFileDeleted() {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    assertTrue(template.collectionExists(Instance.class));
    assertEquals(6, template.findAll(Instance.class).size());

    instancesJson.delete();
    template.reLoadDB();

    assertFalse(template.collectionExists(Instance.class));
    assertFalse(template.collectionExists("instances"));
  }

  @Test
  public void testCollectionExistsForUnknownCollectionName() {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    assertFalse(template.collectionExists("not-a-real-collection"));
  }

  @Test
  public void testListenerDelegationThroughTemplate() {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    assertFalse(template.hasCollectionFileChangeListener());

    CollectionFileChangeAdapter adapter = new CollectionFileChangeAdapter() {
    };
    template.addCollectionFileChangeListener(adapter);
    assertTrue(template.hasCollectionFileChangeListener());

    template.removeCollectionFileChangeListener(adapter);
    assertFalse(template.hasCollectionFileChangeListener());
  }
}
