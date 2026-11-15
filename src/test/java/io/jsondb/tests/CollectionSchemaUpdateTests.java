/*
 * Copyright (c) 2016 - 2026 Farooq Khan
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.query.ddl.AddOperation;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.query.ddl.DeleteOperation;
import io.jsondb.query.ddl.IOperation;
import io.jsondb.query.ddl.RenameOperation;
import io.jsondb.tests.model.LoadBalancer;
import io.jsondb.tests.util.TestUtils;

/**
 * @version 1.0 25-Oct-2016
 */
public class CollectionSchemaUpdateTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/collectionUpdateTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File loadbalancerJson = new File(dbFilesFolder, "loadbalancer.json");

  private JsonDBTemplate jsonDBTemplate = null;
  
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/loadbalancer.json"), loadbalancerJson);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", null, true, null);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  /**
   * Writes loadbalancer.json at schema 0.5 using the given JSON keys for the values that
   * LoadBalancer 1.0 calls "username" and "hostname", as a file from before a field rename would be.
   */
  private void writeOldSchemaFile(String usernameKey, String hostnameKey) throws Exception {
    List<String> lines = new ArrayList<String>();
    lines.add("{\"schemaVersion\":\"0.5\"}");
    for (int i = 1; i <= 3; i++) {
      lines.add(String.format("{\"id\":\"%03d\",\"%s\":\"eclb-54-%02d\",\"%s\":\"admin%d\",\"deletedField\":\"yes\"}",
          i, hostnameKey, i, usernameKey, i));
    }
    java.nio.file.Files.write(loadbalancerJson.toPath(), lines, StandardCharsets.UTF_8);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", null, true, null);
  }

  /**
   * The class field was renamed from "user" to "username" and the file still has "user".
   * The values must survive the rename (they used to be lost).
   */
  @Test
  public void test_RenameField() throws Exception {
    writeOldSchemaFile("user", "hostname");
    assertTrue(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));

    IOperation renOperation = new RenameOperation("username");
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("user", renOperation);

    Map<String, IOperation> allUpdateOps = cu.getUpdateData();
    assertEquals(1, allUpdateOps.size());

    jsonDBTemplate.updateCollectionSchema(cu, LoadBalancer.class);

    assertFalse(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));
    assertEquals("admin2", jsonDBTemplate.findById("002", LoadBalancer.class).getUsername());

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"username\":\"admin1\",\"osName\":null}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"username\":\"admin2\",\"osName\":null}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"username\":\"admin3\",\"osName\":null}"};

    TestUtils.checkLastLines(loadbalancerJson, expectedLinesAtEnd);
  }

  /**
   * Several renames in one update must all take effect (issue #57: only the last one used to)
   */
  @Test
  public void test_MultipleRenames() throws Exception {
    writeOldSchemaFile("user", "host");

    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("user", new RenameOperation("username"))
        .set("host", new RenameOperation("hostname"));
    jsonDBTemplate.updateCollectionSchema(cu, LoadBalancer.class);

    LoadBalancer lb = jsonDBTemplate.findById("003", LoadBalancer.class);
    assertEquals("admin3", lb.getUsername());
    assertEquals("eclb-54-03", lb.getHostname());

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"username\":\"admin1\",\"osName\":null}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"username\":\"admin2\",\"osName\":null}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"username\":\"admin3\",\"osName\":null}"};

    TestUtils.checkLastLines(loadbalancerJson, expectedLinesAtEnd);
  }

  /**
   * Rename, add and delete in the same update
   */
  @Test
  public void test_RenameAddAndDeleteTogether() throws Exception {
    writeOldSchemaFile("user", "hostname");

    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("user", new RenameOperation("username"))
        .set("osName", new AddOperation("linux", false))
        .set("deletedField", new DeleteOperation());
    jsonDBTemplate.updateCollectionSchema(cu, LoadBalancer.class);

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"username\":\"admin1\",\"osName\":\"linux\"}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"username\":\"admin2\",\"osName\":\"linux\"}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"username\":\"admin3\",\"osName\":\"linux\"}"};

    TestUtils.checkLastLines(loadbalancerJson, expectedLinesAtEnd);
  }

  private ReentrantReadWriteLock collectionLock(String collectionName) throws Exception {
    Field cmdMapField = JsonDBTemplate.class.getDeclaredField("cmdMap");
    cmdMapField.setAccessible(true);
    Map<?, ?> cmdMap = (Map<?, ?>) cmdMapField.get(jsonDBTemplate);
    Object cmd = cmdMap.get(collectionName);
    Method getLock = cmd.getClass().getDeclaredMethod("getCollectionLock");
    getLock.setAccessible(true);
    return (ReentrantReadWriteLock) getLock.invoke(cmd);
  }

  /**
   * Writes loadbalancer.json at schema 0.5 with the given documents
   */
  private void writeOldSchemaDocs(String... docs) throws Exception {
    List<String> lines = new ArrayList<String>();
    lines.add("{\"schemaVersion\":\"0.5\"}");
    for (String doc : docs) {
      lines.add(doc);
    }
    java.nio.file.Files.write(loadbalancerJson.toPath(), lines, StandardCharsets.UTF_8);
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", null, true, null);
  }

  /**
   * Asserts the update is rejected before anything is written
   */
  private void assertRejectedUnchanged(CollectionSchemaUpdate cu, String expectedMessagePart) throws Exception {
    byte[] before = java.nio.file.Files.readAllBytes(loadbalancerJson.toPath());
    try {
      jsonDBTemplate.updateCollectionSchema(cu, LoadBalancer.class);
      fail("Expected the schema update to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue("message was: " + expected.getMessage(), expected.getMessage().contains(expectedMessagePart));
    }
    assertArrayEquals(before, java.nio.file.Files.readAllBytes(loadbalancerJson.toPath()));
    assertFalse("collection write lock is still held", collectionLock("loadbalancer").isWriteLocked());
  }

  @Test
  public void test_SwappedRenames() throws Exception {
    writeOldSchemaDocs("{\"id\":\"001\",\"hostname\":\"u1\",\"username\":\"h1\"}");
    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("hostname", new RenameOperation("username"))
        .set("username", new RenameOperation("hostname")), LoadBalancer.class);
    LoadBalancer lb = jsonDBTemplate.findById("001", LoadBalancer.class);
    assertEquals("h1", lb.getHostname());
    assertEquals("u1", lb.getUsername());
  }

  @Test
  public void test_ChainedRenames() throws Exception {
    writeOldSchemaDocs("{\"id\":\"001\",\"user\":\"u1\",\"username\":\"h1\"}");
    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username"))
        .set("username", new RenameOperation("hostname")), LoadBalancer.class);
    LoadBalancer lb = jsonDBTemplate.findById("001", LoadBalancer.class);
    assertEquals("u1", lb.getUsername());
    assertEquals("h1", lb.getHostname());
  }

  @Test
  public void test_RenameOntoExistingValueIsRejected() throws Exception {
    writeOldSchemaDocs("{\"id\":\"001\",\"user\":\"u1\"}", "{\"id\":\"002\",\"user\":\"u2\",\"username\":\"already\"}");
    assertRejectedUnchanged(CollectionSchemaUpdate.update("user", new RenameOperation("username")), "002");
  }

  @Test
  public void test_RenameOntoNullValueIsAllowed() throws Exception {
    writeOldSchemaDocs("{\"id\":\"001\",\"user\":\"u1\",\"username\":null}");
    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username")), LoadBalancer.class);
    assertEquals("u1", jsonDBTemplate.findById("001", LoadBalancer.class).getUsername());
  }

  @Test
  public void test_RenameToUnknownPropertyIsRejected() throws Exception {
    writeOldSchemaFile("user", "hostname");
    assertRejectedUnchanged(CollectionSchemaUpdate.update("user", new RenameOperation("usrname")), "usrname");
  }

  @Test
  public void test_AddOfUnknownPropertyIsRejected() throws Exception {
    writeOldSchemaFile("username", "hostname");
    assertRejectedUnchanged(CollectionSchemaUpdate.update("extra", new AddOperation("x", false)), "extra");
  }

  @Test
  public void test_DeleteOfClassPropertyIsRejected() throws Exception {
    writeOldSchemaFile("username", "hostname");
    assertRejectedUnchanged(CollectionSchemaUpdate.update("username", new DeleteOperation()), "username");
  }

  @Test
  public void test_SecretAddWithoutCipherIsRejected() throws Exception {
    writeOldSchemaFile("username", "hostname");
    assertRejectedUnchanged(CollectionSchemaUpdate.update("osName", new AddOperation("x", true)), "cipher");
  }

  /**
   * Add fills only documents where the key is missing or null, as the CollectionSchemaUpdate javadoc says
   */
  @Test
  public void test_AddKeepsExistingValues() throws Exception {
    writeOldSchemaDocs("{\"id\":\"001\",\"username\":\"u1\"}", "{\"id\":\"002\",\"username\":\"u2\",\"osName\":\"win\"}",
        "{\"id\":\"003\",\"username\":\"u3\",\"osName\":null}");
    jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("osName", new AddOperation("mac", false)), LoadBalancer.class);
    assertEquals("mac", jsonDBTemplate.findById("001", LoadBalancer.class).getOsName());
    assertEquals("win", jsonDBTemplate.findById("002", LoadBalancer.class).getOsName());
    assertEquals("mac", jsonDBTemplate.findById("003", LoadBalancer.class).getOsName());
  }

  /**
   * Reading the collection file fails: the collection lock and the file lock must both be released,
   * so the collection works again once the file is back
   */
  @Test
  public void test_UnreadableFileReleasesLocks() throws Exception {
    writeOldSchemaFile("user", "hostname");
    byte[] original = java.nio.file.Files.readAllBytes(loadbalancerJson.toPath());
    assertTrue(loadbalancerJson.delete());
    assertTrue(loadbalancerJson.mkdir());

    try {
      jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username")),
          LoadBalancer.class);
      fail("Expected the schema update to fail");
    } catch (JsonDBException expected) {
      // Failing is the correct outcome
    }
    assertFalse("collection write lock is still held", collectionLock("loadbalancer").isWriteLocked());

    assertTrue(loadbalancerJson.delete());
    java.nio.file.Files.write(loadbalancerJson.toPath(), original);
    jsonDBTemplate.reloadCollection("loadbalancer");
    assertEquals(3, jsonDBTemplate.findAll(LoadBalancer.class).size());
  }

  /**
   * Writing the rewritten file fails after it was read: the collection lock must be released and
   * the file left as it was
   */
  @Test
  public void test_FailedWriteReleasesLock() throws Exception {
    writeOldSchemaFile("user", "hostname");
    byte[] before = java.nio.file.Files.readAllBytes(loadbalancerJson.toPath());
    // A read-only database folder lets the file be read but not the temporary file be created
    assertTrue(dbFilesFolder.setWritable(false));
    try {
      jsonDBTemplate.updateCollectionSchema(CollectionSchemaUpdate.update("user", new RenameOperation("username")),
          LoadBalancer.class);
      fail("Expected the schema update to fail");
    } catch (JsonDBException expected) {
      // Failing is the correct outcome
    } finally {
      assertTrue(dbFilesFolder.setWritable(true));
    }
    assertFalse("collection write lock is still held", collectionLock("loadbalancer").isWriteLocked());
    assertArrayEquals(before, java.nio.file.Files.readAllBytes(loadbalancerJson.toPath()));
  }

  @Test
  public void test_AddDeleteField() {
    assertTrue(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));

    IOperation addOperation = new AddOperation("mac", false);
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("osName", addOperation);

    jsonDBTemplate.updateCollectionSchema(cu, LoadBalancer.class);

    assertFalse(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"004\",\"hostname\":\"eclb-54-04\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"005\",\"hostname\":\"eclb-54-05\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"006\",\"hostname\":\"eclb-54-06\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"007\",\"hostname\":\"eclb-54-07\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"008\",\"hostname\":\"eclb-54-08\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"009\",\"hostname\":\"eclb-54-09\",\"username\":\"admin\",\"osName\":\"mac\"}",
        "{\"id\":\"010\",\"hostname\":\"eclb-54-10\",\"username\":\"admin\",\"osName\":\"mac\"}"};

    TestUtils.checkLastLines(loadbalancerJson, expectedLinesAtEnd);
  }
  
  @Test
  public void test_OnlyDeleteField() {
    assertTrue(jsonDBTemplate.isCollectionReadonly("loadbalancer"));

    IOperation delOperation = new DeleteOperation();
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("deletedField", delOperation);

    jsonDBTemplate.updateCollectionSchema(cu, "loadbalancer");

    assertFalse(jsonDBTemplate.isCollectionReadonly("loadbalancer"));

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"004\",\"hostname\":\"eclb-54-04\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"005\",\"hostname\":\"eclb-54-05\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"006\",\"hostname\":\"eclb-54-06\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"007\",\"hostname\":\"eclb-54-07\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"008\",\"hostname\":\"eclb-54-08\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"009\",\"hostname\":\"eclb-54-09\",\"username\":\"admin\",\"osName\":null}",
        "{\"id\":\"010\",\"hostname\":\"eclb-54-10\",\"username\":\"admin\",\"osName\":null}"};

    TestUtils.checkLastLines(loadbalancerJson, expectedLinesAtEnd);
  }
  
  @Test
  public void test_RenameInNonExistingCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    IOperation renOperation = new RenameOperation("admin");
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("username", renOperation);

    jsonDBTemplate.updateCollectionSchema(cu, "sites");
  }
  
  @Test
  public void test_AddToNonExistingCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    IOperation addOperation = new AddOperation("mac", false);
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("osName", addOperation);

    jsonDBTemplate.updateCollectionSchema(cu, "sites");
  }
  
  @Test
  public void test_DeleteFromNonExistingCollection() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection by name 'sites' not found. Create collection first");
    
    IOperation delOperation = new DeleteOperation();
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("deletedField", delOperation);

    jsonDBTemplate.updateCollectionSchema(cu, "sites");
  }
}
