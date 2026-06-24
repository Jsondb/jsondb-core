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
