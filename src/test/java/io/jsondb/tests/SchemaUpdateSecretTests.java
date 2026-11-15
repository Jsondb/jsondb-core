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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.Default1Cipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.query.ddl.AddOperation;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.tests.model.Instance;

/**
 * Adding a @Secret field through a schema update must store the default value encrypted, so it reads
 * back decrypted like any other secret and never appears in plain text in the file.
 */
public class SchemaUpdateSecretTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/schemaUpdateSecretTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");

  private ICipher cipher;
  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    // Instance 1.0 has an @Secret privateKey; these documents, from schema 0.9, have none
    java.nio.file.Files.write(instancesJson.toPath(), Arrays.asList(
        "{\"schemaVersion\":\"0.9\"}",
        "{\"id\":\"01\",\"hostname\":\"h1\",\"publicKey\":\"p1\"}",
        "{\"id\":\"02\",\"hostname\":\"h2\",\"publicKey\":\"p2\"}"), StandardCharsets.UTF_8);
    cipher = new Default1Cipher("1r8+24pibarAWgS85/Heeg==");
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher, true, null);
  }

  @After
  public void tearDown() throws Exception {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  @Test
  public void testAddedSecretDefaultIsEncryptedOnDiskAndDecryptedOnRead() throws Exception {
    assertTrue(jsonDBTemplate.isCollectionReadonly(Instance.class));

    jsonDBTemplate.updateCollectionSchema(
        CollectionSchemaUpdate.update("privateKey", new AddOperation("initial-secret", true)), Instance.class);

    assertFalse(jsonDBTemplate.isCollectionReadonly(Instance.class));
    assertEquals("initial-secret", jsonDBTemplate.findById("01", Instance.class).getPrivateKey());
    assertEquals("initial-secret", jsonDBTemplate.findById("02", Instance.class).getPrivateKey());

    ObjectMapper mapper = new ObjectMapper();
    List<String> lines = java.nio.file.Files.readAllLines(instancesJson.toPath(), StandardCharsets.UTF_8);
    for (String line : lines.subList(1, lines.size())) {
      String stored = mapper.readTree(line).get("privateKey").asText();
      assertNotEquals("the secret must not be stored in plain text", "initial-secret", stored);
      assertEquals("initial-secret", cipher.decrypt(stored));
    }

    jsonDBTemplate.reLoadDB();
    assertEquals("initial-secret", jsonDBTemplate.findById("01", Instance.class).getPrivateKey());
  }
}
