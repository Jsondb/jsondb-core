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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.Update;
import io.jsondb.tests.model.LoadBalancer;

/**
 * A collection whose file has a different schema version than its class is loaded read-only until
 * updateCollectionSchema() migrates it. Every write through the public API must be rejected and leave
 * the file untouched, while reads keep working.
 */
public class ReadonlyCollectionTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/readonlyCollectionTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File loadbalancerJson = new File(dbFilesFolder, "loadbalancer.json");

  private JsonDBTemplate jsonDBTemplate = null;

  private interface Operation {
    void run() throws Exception;
  }

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    // The fixture is at schema 0.5 while LoadBalancer is 1.0, and it has a field the class no longer has
    Files.copy(new File("src/test/resources/dbfiles/loadbalancer.json"), loadbalancerJson);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", null, true, null);
  }

  @After
  public void tearDown() throws Exception {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private static LoadBalancer loadBalancer(String id) {
    LoadBalancer lb = new LoadBalancer();
    lb.setId(id);
    lb.setHostname("eclb-new-" + id);
    return lb;
  }

  @Test
  public void testCollectionIsReadonlyAndReadable() {
    assertTrue(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));
    assertEquals(10, jsonDBTemplate.findAll(LoadBalancer.class).size());
    assertEquals("eclb-54-03", jsonDBTemplate.findById("003", LoadBalancer.class).getHostname());
  }

  @Test
  public void testEveryWriteIsRejectedAndTheFileIsUntouched() throws Exception {
    final LoadBalancer existing = jsonDBTemplate.findById("001", LoadBalancer.class);
    Map<String, Operation> writes = new LinkedHashMap<String, Operation>();
    writes.put("insert", () -> jsonDBTemplate.insert(loadBalancer("100")));
    writes.put("insert(batch)", () -> jsonDBTemplate.insert(Arrays.asList(loadBalancer("101"), loadBalancer("102")), LoadBalancer.class));
    writes.put("save", () -> jsonDBTemplate.save(existing, LoadBalancer.class));
    writes.put("upsert", () -> jsonDBTemplate.upsert(loadBalancer("103")));
    writes.put("upsert(batch)", () -> jsonDBTemplate.upsert(Arrays.asList(existing, loadBalancer("104")), LoadBalancer.class));
    writes.put("remove", () -> jsonDBTemplate.remove(existing, LoadBalancer.class));
    writes.put("remove(batch)", () -> jsonDBTemplate.remove(Arrays.asList(existing), LoadBalancer.class));
    writes.put("findAndRemove", () -> jsonDBTemplate.findAndRemove("/.[id='002']", LoadBalancer.class));
    writes.put("findAllAndRemove", () -> jsonDBTemplate.findAllAndRemove("/.", LoadBalancer.class));
    writes.put("findAndModify", () -> jsonDBTemplate.findAndModify("/.[id='002']", Update.update("hostname", "x"), LoadBalancer.class));
    writes.put("findAllAndModify", () -> jsonDBTemplate.findAllAndModify("/.", Update.update("hostname", "x"), LoadBalancer.class));

    byte[] before = java.nio.file.Files.readAllBytes(loadbalancerJson.toPath());
    StringBuilder wrong = new StringBuilder();
    for (Map.Entry<String, Operation> write : writes.entrySet()) {
      try {
        write.getValue().run();
        wrong.append("\n  ").append(write.getKey()).append(" was not rejected");
      } catch (InvalidJsonDbApiUsageException expected) {
        if (!expected.getMessage().contains("readonly")) {
          wrong.append("\n  ").append(write.getKey()).append(" failed for another reason: ").append(expected.getMessage());
        }
      } catch (Exception e) {
        wrong.append("\n  ").append(write.getKey()).append(" threw ").append(e);
      }
    }
    if (wrong.length() > 0) {
      fail("Writes to a read-only collection that were not rejected cleanly:" + wrong);
    }

    assertArrayEquals(before, java.nio.file.Files.readAllBytes(loadbalancerJson.toPath()));
    assertEquals(10, jsonDBTemplate.findAll(LoadBalancer.class).size());
    jsonDBTemplate.reLoadDB();
    assertEquals(10, jsonDBTemplate.findAll(LoadBalancer.class).size());
  }
}
