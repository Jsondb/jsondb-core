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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;

import io.jsondb.CollectionMetaData;
import io.jsondb.JsonDBConfig;
import io.jsondb.Util;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.tests.model.SecureVolume;
import io.jsondb.tests.model.Volume;

/**
 * @version 1.0 06-Oct-2016
 */
public class CollectionMetaDataTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/collectionMetadataTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");
  ICipher cipher = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  @Test
  public void test_MetadataLoad_Simple() {
    Volume volume = new Volume();
    CollectionMetaData cmd = new CollectionMetaData("volumes", volume.getClass(), "1.0", null);

    assertEquals("id", cmd.getIdAnnotatedFieldName());
    assertEquals("getId", cmd.getIdAnnotatedFieldGetterMethod().getName());
    assertEquals("setId", cmd.getIdAnnotatedFieldSetterMethod().getName());

    List<String> secretAnnotatedFieldNames = cmd.getSecretAnnotatedFieldNames();
    assertEquals(0, secretAnnotatedFieldNames.size());
    assertEquals(false, cmd.isSecretField("SomeField"));

    assertEquals("setName", cmd.getSetterMethodForFieldName("name").getName());
    assertEquals("getName", cmd.getGetterMethodForFieldName("name").getName());

    assertEquals("setFlash", cmd.getSetterMethodForFieldName("flash").getName());
    assertEquals("isFlash", cmd.getGetterMethodForFieldName("flash").getName());

    assertFalse(cmd.hasSecret());

    assertEquals("1.0", cmd.getSchemaVersion());
    
    assertNull(cmd.getActualSchemaVersion());
    
    assertEquals("volumes", cmd.getCollectionName());
  }

  @Test
  public void test_MetadataLoad_CollectionWithSecret() {
    SecureVolume server = new SecureVolume();
    CollectionMetaData cmd = new CollectionMetaData("servers", server.getClass(), "1.0", null);

    assertEquals("id", cmd.getIdAnnotatedFieldName());
    assertEquals("getId", cmd.getIdAnnotatedFieldGetterMethod().getName());
    assertEquals("setId", cmd.getIdAnnotatedFieldSetterMethod().getName());

    List<String> secretAnnotatedFieldNames = cmd.getSecretAnnotatedFieldNames();
    assertEquals(1, secretAnnotatedFieldNames.size());
    assertEquals(true, cmd.isSecretField("encryptionKey"));

    assertTrue(cmd.hasSecret());
  }

  @Test
  public void test_MetadataLoad_UsingBuilder() {
    JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", cipher, false, null);
    Map<String, CollectionMetaData> cmdMap = CollectionMetaData.builder(dbConfig);
    CollectionMetaData cmd = cmdMap.get("instances");
    assertNotNull(cmd);
    assertEquals("1.0", cmd.getSchemaVersion());
    assertEquals("id", cmd.getIdAnnotatedFieldName());
    assertEquals("getId", cmd.getIdAnnotatedFieldGetterMethod().getName());
    assertEquals("setId", cmd.getIdAnnotatedFieldSetterMethod().getName());
  }
}
