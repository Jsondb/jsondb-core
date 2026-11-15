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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.jsondb.CollectionMetaData;
import io.jsondb.crypto.CryptoUtil;
import io.jsondb.crypto.Default1Cipher;
import io.jsondb.tests.model.ExtendedSecureVolume;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.PojoWithThrowingSecretGetter;

public class CryptoUtilTests {

  @Test
  public void testEncryptAndDecryptSecretFields() throws Exception {
    String key = CryptoUtil.generate128BitKey("test-password", "test-salt");
    Default1Cipher cipher = new Default1Cipher(key);

    Instance instance = new Instance();
    instance.setId("99");
    instance.setHostname("host");
    instance.setPrivateKey("top-secret");
    instance.setPublicKey("public");

    CollectionMetaData cmd = new CollectionMetaData("instances", Instance.class, "1.0", null);

    CryptoUtil.encryptFields(instance, cmd, cipher);
    assertNotEquals("top-secret", instance.getPrivateKey());
    assertEquals("public", instance.getPublicKey());

    CryptoUtil.decryptFields(instance, cmd, cipher);
    assertEquals("top-secret", instance.getPrivateKey());
  }

  @Test
  public void testEncryptFieldsSkipsNullSecret() throws Exception {
    String key = CryptoUtil.generate128BitKey("test-password", "test-salt");
    Default1Cipher cipher = new Default1Cipher(key);

    Instance instance = new Instance();
    instance.setId("99");
    instance.setPrivateKey(null);

    CollectionMetaData cmd = new CollectionMetaData("instances", Instance.class, "1.0", null);
    CryptoUtil.encryptFields(instance, cmd, cipher);

    assertNull(instance.getPrivateKey());
  }

  @Test
  public void testEncryptFieldsPropagatesGetterFailure() throws Exception {
    String key = CryptoUtil.generate128BitKey("test-password", "test-salt");
    Default1Cipher cipher = new Default1Cipher(key);

    PojoWithThrowingSecretGetter pojo = new PojoWithThrowingSecretGetter();
    pojo.setId("1");
    CollectionMetaData cmd = new CollectionMetaData("throwingsecretgetter", PojoWithThrowingSecretGetter.class, "1.0", null);

    try {
      CryptoUtil.encryptFields(pojo, cmd, cipher);
      org.junit.Assert.fail("Expected InvocationTargetException");
    } catch (java.lang.reflect.InvocationTargetException e) {
      org.junit.Assert.assertTrue(e.getCause() instanceof RuntimeException);
      org.junit.Assert.assertEquals("secret getter failed", e.getCause().getMessage());
    }
  }

  @Test
  public void testEncryptAndDecryptInheritedSecretField() throws Exception {
    String key = CryptoUtil.generate128BitKey("test-password", "test-salt");
    Default1Cipher cipher = new Default1Cipher(key);

    ExtendedSecureVolume volume = new ExtendedSecureVolume();
    volume.setId("vol-1");
    volume.setEncryptionKey("inherited-secret");
    volume.setZone("us-east");

    CollectionMetaData cmd = new CollectionMetaData("extendedsecurevolumes", ExtendedSecureVolume.class, "1.0", null);

    CryptoUtil.encryptFields(volume, cmd, cipher);
    assertNotEquals("inherited-secret", volume.getEncryptionKey());

    CryptoUtil.decryptFields(volume, cmd, cipher);
    assertEquals("inherited-secret", volume.getEncryptionKey());
  }
}
