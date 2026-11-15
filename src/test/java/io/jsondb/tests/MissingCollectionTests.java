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
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.Update;
import io.jsondb.query.ddl.AddOperation;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.tests.model.Site;

/**
 * Every operation on a collection that does not exist must fail with InvalidJsonDbApiUsageException.
 * That covers a known class whose collection was never created, and a collection name no class has
 * (e.g. a typo). For unknown names nine operations used to fail with a NullPointerException instead.
 */
public class MissingCollectionTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/missingCollectionTests";
  private File dbFilesFolder = new File(dbFilesLocation);

  private JsonDBTemplate jsonDBTemplate = null;
  private Site site;

  private interface Operation {
    void run() throws Exception;
  }

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    site = new Site();
    site.setId("1");
  }

  @After
  public void tearDown() throws Exception {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private Map<String, Operation> operationsOn(final String name) {
    Map<String, Operation> ops = new LinkedHashMap<String, Operation>();
    ops.put("find", () -> jsonDBTemplate.find(".", name));
    ops.put("findAll", () -> jsonDBTemplate.findAll(name));
    ops.put("findById", () -> jsonDBTemplate.findById("1", name));
    ops.put("findOne", () -> jsonDBTemplate.findOne(".", name));
    ops.put("count", () -> jsonDBTemplate.count(name));
    ops.put("insert", () -> jsonDBTemplate.insert(site, name));
    ops.put("insert(batch)", () -> jsonDBTemplate.insert(Arrays.asList(site), name));
    ops.put("save", () -> jsonDBTemplate.save(site, name));
    ops.put("upsert", () -> jsonDBTemplate.upsert(site, name));
    ops.put("upsert(batch)", () -> jsonDBTemplate.upsert(Arrays.asList(site), name));
    ops.put("remove", () -> jsonDBTemplate.remove(site, name));
    ops.put("remove(batch)", () -> jsonDBTemplate.remove(Arrays.asList(site), name));
    ops.put("findAndRemove", () -> jsonDBTemplate.findAndRemove(".", name));
    ops.put("findAllAndRemove", () -> jsonDBTemplate.findAllAndRemove(".", name));
    ops.put("findAndModify", () -> jsonDBTemplate.findAndModify(".", Update.update("location", "x"), name));
    ops.put("findAllAndModify", () -> jsonDBTemplate.findAllAndModify(".", Update.update("location", "x"), name));
    ops.put("dropCollection", () -> jsonDBTemplate.dropCollection(name));
    ops.put("reloadCollection", () -> jsonDBTemplate.reloadCollection(name));
    ops.put("updateCollectionSchema", () -> jsonDBTemplate.updateCollectionSchema(
        CollectionSchemaUpdate.update("location", new AddOperation("x", false)), name));
    return ops;
  }

  private void assertEveryOperationRejected(String name) {
    StringBuilder wrong = new StringBuilder();
    for (Map.Entry<String, Operation> op : operationsOn(name).entrySet()) {
      try {
        op.getValue().run();
        wrong.append("\n  ").append(op.getKey()).append(" did not throw");
      } catch (InvalidJsonDbApiUsageException expected) {
        if (!expected.getMessage().contains("'" + name + "'")) {
          wrong.append("\n  ").append(op.getKey()).append(" did not name the collection: ").append(expected.getMessage());
        }
      } catch (Exception e) {
        wrong.append("\n  ").append(op.getKey()).append(" threw ").append(e);
      }
    }
    if (wrong.length() > 0) {
      fail("Operations on missing collection '" + name + "' that did not fail cleanly:" + wrong);
    }
  }

  @Test
  public void testEveryOperationOnAnUncreatedCollectionIsRejected() {
    assertEveryOperationRejected("sites");
  }

  @Test
  public void testEveryOperationOnAnUnknownCollectionNameIsRejected() {
    assertEveryOperationRejected("nosuch");
  }

  @Test
  public void testCollectionExistsIsFalseForMissingCollections() {
    assertFalse(jsonDBTemplate.collectionExists("sites"));
    assertFalse(jsonDBTemplate.collectionExists("nosuch"));
  }

  @Test
  public void testIsCollectionReadonlyRejectsAnUnknownName() {
    try {
      jsonDBTemplate.isCollectionReadonly("nosuch");
      fail("Expected an unknown collection name to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("'nosuch'"));
    }
  }

  @Test
  public void testRemovingAListenerWhenNoneWasAddedIsHarmless() {
    jsonDBTemplate.removeCollectionFileChangeListener(new io.jsondb.events.CollectionFileChangeAdapter() {});
    assertFalse(jsonDBTemplate.hasCollectionFileChangeListener());
  }
}
