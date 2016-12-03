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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;

import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.JsonDBConfig;
import io.jsondb.crypto.ICipher;

/**
 * @version 1.0 08-Oct-2016
 */
public class JsonDBConfigTests {

  private String dbFilesLocation = "src/test/resources/dbfiles/dbConfigTests";

  @Test
  public void testDbConfig() {
    JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false,
        new DefaultSchemaVersionComparator());

    assertEquals("src/test/resources/dbfiles/dbConfigTests", dbConfig.getDbFilesLocationString());
    assertEquals(new File("src/test/resources/dbfiles/dbConfigTests"), dbConfig.getDbFilesLocation());
    assertEquals(new File("src/test/resources/dbfiles/dbConfigTests").toPath(), dbConfig.getDbFilesPath());
    assertEquals(Charset.forName("UTF-8"), dbConfig.getCharset());
    assertNull(dbConfig.getCipher());
    assertFalse(dbConfig.isCompatibilityMode());
    
    dbConfig.setDbFilesLocationString("myfolder");
    assertEquals("myfolder", dbConfig.getDbFilesLocationString());
    assertEquals(new File("myfolder"), dbConfig.getDbFilesLocation());
    assertEquals(new File("myfolder").toPath(), dbConfig.getDbFilesPath());
    
    Charset newCharset = Charset.forName("UTF-16");
    dbConfig.setCharset(newCharset);
    assertEquals(newCharset, dbConfig.getCharset());
    
    ICipher mCipher = new MyCipher();
    dbConfig.setCipher(mCipher);
    assertEquals(mCipher, dbConfig.getCipher());
    
    dbConfig.setBaseScanPackage("io.newpackage");
    assertEquals("io.newpackage", dbConfig.getBaseScanPackage());
    
    ObjectMapper mapper = dbConfig.getObjectMapper();
    assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    dbConfig.setCompatibilityMode(true);
    assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    dbConfig.setCompatibilityMode(false);
    assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    
    ObjectMapper newMapper = new ObjectMapper();
    dbConfig.setObjectMapper(newMapper);
    assertEquals(newMapper, dbConfig.getObjectMapper());
  }
  
  
  private class MyCipher implements ICipher {

    /* (non-Javadoc)
     * @see io.jsondb.crypto.ICipher#encrypt(java.lang.String)
     */
    @Override
    public String encrypt(String plainText) {
      // TODO Auto-generated method stub
      return null;
    }

    /* (non-Javadoc)
     * @see io.jsondb.crypto.ICipher#decrypt(java.lang.String)
     */
    @Override
    public String decrypt(String cipherText) {
      // TODO Auto-generated method stub
      return null;
    }
    
  }
}
