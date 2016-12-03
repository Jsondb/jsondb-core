/*
 * Copyright (c) 2016 Farooq Khan
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.InvalidJsonDbApiUsageException;
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

  @Test
  public void test_RenameField() {
    assertTrue(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));

    IOperation renOperation = new RenameOperation("admin");
    CollectionSchemaUpdate cu = CollectionSchemaUpdate.update("username", renOperation);
    
    Map<String, IOperation> allUpdateOps = cu.getUpdateData();
    assertEquals(1, allUpdateOps.size());

    jsonDBTemplate.updateCollectionSchema(cu, LoadBalancer.class);

    assertFalse(jsonDBTemplate.isCollectionReadonly(LoadBalancer.class));

    String[] expectedLinesAtEnd = {
        "{\"schemaVersion\":\"1.0\"}",
        "{\"id\":\"001\",\"hostname\":\"eclb-54-01\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"002\",\"hostname\":\"eclb-54-02\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"003\",\"hostname\":\"eclb-54-03\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"004\",\"hostname\":\"eclb-54-04\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"005\",\"hostname\":\"eclb-54-05\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"006\",\"hostname\":\"eclb-54-06\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"007\",\"hostname\":\"eclb-54-07\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"008\",\"hostname\":\"eclb-54-08\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"009\",\"hostname\":\"eclb-54-09\",\"admin\":\"admin\",\"osName\":null}",
        "{\"id\":\"010\",\"hostname\":\"eclb-54-10\",\"admin\":\"admin\",\"osName\":null}"};

    TestUtils.checkLastLines(loadbalancerJson, expectedLinesAtEnd);
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
