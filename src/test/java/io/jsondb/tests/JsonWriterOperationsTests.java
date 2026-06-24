package io.jsondb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.CollectionMetaData;
import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.io.JsonWriter;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.util.TestUtils;

public class JsonWriterOperationsTests {

  private static final String DB = "src/test/resources/dbfiles/jsonWriterOperationsTests";
  private File dbFolder = new File(DB);
  private File instancesJson = new File(dbFolder, "instances.json");

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFolder.mkdirs();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
  }

  @After
  public void tearDown() {
    Util.delete(dbFolder);
  }

  private JsonDBConfig dbConfig() {
    return new JsonDBConfig(DB, "io.jsondb.tests.model", null, false, new DefaultSchemaVersionComparator());
  }

  private CollectionMetaData cmd() {
    return new CollectionMetaData("instances", Instance.class, "1.0", new DefaultSchemaVersionComparator());
  }

  @Test
  public void testAppendSingleObject() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    List<Instance> existing = template.findAll(Instance.class);

    Instance added = new Instance();
    added.setId("07");
    added.setHostname("ec2-new");
    added.setPrivateKey("secret");
    added.setPublicKey("pub");

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.appendToJsonFile(existing, added));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"07\",\"hostname\":\"ec2-new\",\"privateKey\":\"secret\",\"publicKey\":\"pub\"}"
    });
  }

  @Test
  public void testAppendBatch() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    List<Instance> existing = template.findAll(Instance.class);

    List<Instance> batch = new ArrayList<Instance>();
    Instance one = new Instance();
    one.setId("08");
    one.setHostname("batch-1");
    batch.add(one);
    Instance two = new Instance();
    two.setId("09");
    two.setHostname("batch-2");
    batch.add(two);

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.appendToJsonFile(existing, batch));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"08\",\"hostname\":\"batch-1\",\"privateKey\":null,\"publicKey\":null}",
        "{\"id\":\"09\",\"hostname\":\"batch-2\",\"privateKey\":null,\"publicKey\":null}"
    });
  }

  @Test
  public void testRemoveSingleId() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    Map<Object, Instance> map = new HashMap<Object, Instance>();
    for (Instance instance : template.findAll(Instance.class)) {
      map.put(instance.getId(), instance);
    }

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.removeFromJsonFile(map, "06"));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"05\",\"hostname\":\"ec2-54-191-02\",\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\",\"publicKey\":\"d3aa045f71bf4d1dffd2c5f485a4bc1d\"}"
    });
  }

  @Test
  public void testRemoveMultipleIds() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    Map<Object, Instance> map = new HashMap<Object, Instance>();
    for (Instance instance : template.findAll(Instance.class)) {
      map.put(instance.getId(), instance);
    }

    Set<Object> removeIds = new HashSet<Object>();
    removeIds.add("05");
    removeIds.add("06");

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.removeFromJsonFile(map, removeIds));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"04\",\"hostname\":\"ec2-54-191-03\",\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\",\"publicKey\":\"d3aa045f71bf4d1dffd2c5f485a4bc1d\"}"
    });
  }

  @Test
  public void testUpdateSingleObject() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    Map<Object, Instance> map = new HashMap<Object, Instance>();
    for (Instance instance : template.findAll(Instance.class)) {
      map.put(instance.getId(), instance);
    }

    Instance updated = map.get("03");
    updated.setHostname("updated-host");

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.updateInJsonFile(map, "03", updated));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"06\",\"hostname\":\"ec2-54-191-06\",\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\",\"publicKey\":\"\"}"
    });
  }

  @Test
  public void testUpdateMultipleObjects() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    Map<Object, Instance> map = new HashMap<Object, Instance>();
    for (Instance instance : template.findAll(Instance.class)) {
      map.put(instance.getId(), instance);
    }

    Instance updated03 = map.get("03");
    updated03.setHostname("batch-updated-03");
    Instance updated04 = map.get("04");
    updated04.setHostname("batch-updated-04");

    Map<Object, Instance> modified = new HashMap<Object, Instance>();
    modified.put("03", updated03);
    modified.put("04", updated04);

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.updateInJsonFile(map, modified));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"06\",\"hostname\":\"ec2-54-191-06\",\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\",\"publicKey\":\"\"}"
    });
  }

  @Test
  public void testRenameKeyInJsonFile() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    List<Instance> instances = template.findAll(Instance.class);

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.renameKeyInJsonFile(instances, false, "hostname", "hostName"));

    TestUtils.checkLastLines(instancesJson, new String[] {
        "{\"id\":\"06\",\"hostName\":\"ec2-54-191-06\",\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\",\"publicKey\":\"\"}"
    });
  }

  @Test
  public void testReadonlyCollectionRejectsAppend() throws Exception {
    CollectionMetaData readonlyCmd = cmd();
    readonlyCmd.setActualSchemaVersion("0.9");

    JsonWriter writer = new JsonWriter(dbConfig(), readonlyCmd, "instances", instancesJson);

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection is loaded as readonly");
    writer.appendToJsonFile(new ArrayList<Instance>(), new Instance());
  }

  @Test
  public void testReadonlyCollectionRejectsUpdate() throws Exception {
    CollectionMetaData readonlyCmd = cmd();
    readonlyCmd.setActualSchemaVersion("0.9");

    JsonWriter writer = new JsonWriter(dbConfig(), readonlyCmd, "instances", instancesJson);

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection is loaded as readonly");
    writer.updateInJsonFile(new HashMap<Object, Instance>(), "01", new Instance());
  }

  @Test
  public void testReadonlyCollectionRejectsRemove() throws Exception {
    CollectionMetaData readonlyCmd = cmd();
    readonlyCmd.setActualSchemaVersion("0.9");

    JsonWriter writer = new JsonWriter(dbConfig(), readonlyCmd, "instances", instancesJson);

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection is loaded as readonly");
    writer.removeFromJsonFile(new HashMap<Object, Instance>(), "01");
  }

  @Test
  public void testReWriteCollection() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    List<Instance> subset = template.findAll(Instance.class).subList(0, 2);

    JsonWriter writer = new JsonWriter(dbConfig(), cmd(), "instances", instancesJson);
    assertTrue(writer.reWriteJsonFile(subset, false));

    assertEquals(3, TestUtils.getNoOfLinesInFile(instancesJson));
  }

  @Test
  public void testReWriteReadonlyCollectionWhenIgnoreReadonly() throws Exception {
    CollectionMetaData readonlyCmd = cmd();
    readonlyCmd.setActualSchemaVersion("0.9");

    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    List<Instance> subset = template.findAll(Instance.class).subList(0, 1);

    JsonWriter writer = new JsonWriter(dbConfig(), readonlyCmd, "instances", instancesJson);
    assertTrue(writer.reWriteJsonFile(subset, true));

    assertEquals(2, TestUtils.getNoOfLinesInFile(instancesJson));
  }

  @Test
  public void testReadonlyCollectionRejectsWrite() throws Exception {
    CollectionMetaData readonlyCmd = cmd();
    readonlyCmd.setActualSchemaVersion("0.9");

    JsonWriter writer = new JsonWriter(dbConfig(), readonlyCmd, "instances", instancesJson);

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection is loaded as readonly");
    writer.reWriteJsonFile(new ArrayList<Instance>(), false);
  }
}
