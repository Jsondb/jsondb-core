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
