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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;

import io.jsondb.CollectionMetaData;
import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.io.JsonWriter;
import io.jsondb.tests.model.Instance;

/**
 * The complete file every JsonWriter operation produces: the schema version line, every document, in
 * which order, and which document changed. JsonWriterOperationsTests only checks the last lines.
 */
public class JsonWriterOutputTests {
  private static final String DB = "src/test/resources/dbfiles/jsonWriterOutputTests";
  private static final String HEADER = "{\"schemaVersion\":\"1.0\"}";

  private File dbFolder = new File(DB);
  private File instancesJson = new File(dbFolder, "instances.json");
  private JsonDBConfig dbConfig;
  private ObjectMapper mapper;
  private Map<Object, Instance> documents;

  @Before
  public void setUp() throws Exception {
    dbFolder.mkdirs();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    dbConfig = new JsonDBConfig(DB, "io.jsondb.tests.model", null, false, new DefaultSchemaVersionComparator());
    mapper = dbConfig.getObjectMapper();
    documents = new LinkedHashMap<Object, Instance>();
    try (JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model")) {
      for (Instance instance : template.findAll(Instance.class)) {
        documents.put(instance.getId(), instance);
      }
    }
  }

  @After
  public void tearDown() {
    Util.delete(dbFolder);
  }

  private JsonWriter writer() throws Exception {
    return new JsonWriter(dbConfig, new CollectionMetaData("instances", Instance.class, "1.0", new DefaultSchemaVersionComparator()),
        "instances", instancesJson);
  }

  private List<String> fileLines() throws Exception {
    return java.nio.file.Files.readAllLines(instancesJson.toPath(), StandardCharsets.UTF_8);
  }

  /**
   * The expected file: the header, then each document serialized the way JsonDB writes it
   */
  private List<String> expected(List<Instance> docs) throws Exception {
    List<String> lines = new ArrayList<String>();
    lines.add(HEADER);
    for (Instance doc : docs) {
      lines.add(mapper.writeValueAsString(doc));
    }
    return lines;
  }

  private List<Instance> existing() {
    return new ArrayList<Instance>(documents.values());
  }

  private static Instance instance(String id, String hostname) {
    Instance i = new Instance();
    i.setId(id);
    i.setHostname(hostname);
    return i;
  }

  @Test
  public void testRewritingUnchangedDocumentsReproducesTheFile() throws Exception {
    List<String> original = fileLines();
    assertTrue(writer().reWriteJsonFile(existing(), false));
    assertEquals(original, fileLines());
  }

  @Test
  public void testAppendSingleAddsItAtTheEnd() throws Exception {
    Instance added = instance("07", "new");
    assertTrue(writer().appendToJsonFile(existing(), added));
    List<Instance> want = existing();
    want.add(added);
    assertEquals(expected(want), fileLines());
  }

  @Test
  public void testAppendBatchAddsThemAtTheEndInOrder() throws Exception {
    List<Instance> batch = Arrays.asList(instance("07", "a"), instance("08", "b"));
    assertTrue(writer().appendToJsonFile(existing(), batch));
    List<Instance> want = existing();
    want.addAll(batch);
    assertEquals(expected(want), fileLines());
  }

  @Test
  public void testRemoveSingleKeepsTheOthersInOrder() throws Exception {
    assertTrue(writer().removeFromJsonFile(documents, "02"));
    List<Instance> want = existing();
    want.remove(documents.get("02"));
    assertEquals(expected(want), fileLines());
  }

  @Test
  public void testRemoveSeveralKeepsTheOthersInOrder() throws Exception {
    assertTrue(writer().removeFromJsonFile(documents, new HashSet<Object>(Arrays.asList("02", "05"))));
    List<Instance> want = existing();
    want.remove(documents.get("02"));
    want.remove(documents.get("05"));
    assertEquals(expected(want), fileLines());
  }

  @Test
  public void testUpdateSingleReplacesItInPlace() throws Exception {
    Instance updated = instance("03", "updated");
    assertTrue(writer().updateInJsonFile(documents, "03", updated));
    List<Instance> want = existing();
    want.set(2, updated);
    assertEquals(expected(want), fileLines());
  }

  @Test
  public void testUpdateSeveralReplacesThemInPlace() throws Exception {
    Map<Object, Instance> modified = new LinkedHashMap<Object, Instance>();
    modified.put("05", instance("05", "updated-5"));
    modified.put("03", instance("03", "updated-3"));
    assertTrue(writer().updateInJsonFile(documents, modified));
    List<Instance> want = existing();
    want.set(2, modified.get("03"));
    want.set(4, modified.get("05"));
    assertEquals(expected(want), fileLines());
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testRenameKeyRenamesItInEveryDocument() throws Exception {
    assertTrue(writer().renameKeyInJsonFile(existing(), false, "hostname", "hostName"));
    List<String> want = new ArrayList<String>();
    for (String line : expected(existing())) {
      want.add(line.replace("\"hostname\":", "\"hostName\":"));
    }
    assertEquals(want, fileLines());
  }

  @Test
  public void testWritesLeaveNoTemporaryFilesBehind() throws Exception {
    JsonWriter w = writer();
    assertTrue(w.appendToJsonFile(existing(), instance("07", "new")));
    for (String name : dbFolder.list()) {
      assertTrue("unexpected file " + name, name.equals("instances.json") || name.equals("lock"));
    }
  }
}
