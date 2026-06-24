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

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.jsondb.JsonDBException;
import io.jsondb.crypto.CryptoUtil;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.Default1Cipher;
import io.jsondb.crypto.ICipher;

@SuppressWarnings("deprecation")
public class DefaultAESCipherTests {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  
  @Test
  public void testKeyValidityAesCbc() throws UnsupportedEncodingException, GeneralSecurityException {
    expectedException.expect(InvalidKeyException.class);
    expectedException.expectMessage("Failed to create DefaultAESCBCCipher, something wrong with the key");
    
    @SuppressWarnings("unused")
    DefaultAESCBCCipher cipher = new DefaultAESCBCCipher("badkey", "UTF-8");
  }
  
  @Test
  public void testKeyAesCbc() throws UnsupportedEncodingException, GeneralSecurityException {
    String base64EncodedKey = CryptoUtil.generate128BitKey("MyPassword", "ksdfkja923u4anf");
    
    ICipher cipher = new DefaultAESCBCCipher(base64EncodedKey);
    
    String encryptedText = cipher.encrypt("Hallo, Wie gehts");
    assertEquals("cEIxZFDVSlWIN+ZmOGyvmbWv4+ziI884BNu0Hplglws=", encryptedText);
    String decryptedText = cipher.decrypt(encryptedText);
    assertEquals("Hallo, Wie gehts", decryptedText);
  }
  
  @Test
  public void testKeyValidityDefault1() throws UnsupportedEncodingException, GeneralSecurityException {
    expectedException.expect(JsonDBException.class);
    expectedException.expectCause(IsInstanceOf.any(InvalidKeyException.class));
    
    Default1Cipher cipher = new Default1Cipher("badkey", StandardCharsets.UTF_8);
    @SuppressWarnings("unused")
    String encryptedText = cipher.encrypt("Hallo, Wie gehts");
  }
  
  @Test
  public void testDefault1DecryptWithWrongKey() throws UnsupportedEncodingException, GeneralSecurityException {
    String keyA = CryptoUtil.generate128BitKey("password-a", "salt-a");
    String keyB = CryptoUtil.generate128BitKey("password-b", "salt-b");
    Default1Cipher encryptCipher = new Default1Cipher(keyA);
    Default1Cipher decryptCipher = new Default1Cipher(keyB);

    String ciphertext = encryptCipher.encrypt("secret-value");

    expectedException.expect(JsonDBException.class);
    expectedException.expectMessage("Incorrect key for this ciphertext");
    decryptCipher.decrypt(ciphertext);
  }

  @Test
  public void testDefault1ConstructorRejectsNullKey() throws GeneralSecurityException {
    expectedException.expect(NullPointerException.class);
    new Default1Cipher((byte[]) null);
  }

  @Test
  public void testDefault1ConstructorRejectsNullCharset() throws GeneralSecurityException {
    expectedException.expect(NullPointerException.class);
    new Default1Cipher(new byte[16], null);
  }

  @Test
  public void testDefaultAESCBCDecryptWithWrongKey() throws UnsupportedEncodingException, GeneralSecurityException {
    String keyA = CryptoUtil.generate128BitKey("password-a", "salt-a");
    String keyB = CryptoUtil.generate128BitKey("password-b", "salt-b");
    DefaultAESCBCCipher encryptCipher = new DefaultAESCBCCipher(keyA);
    DefaultAESCBCCipher decryptCipher = new DefaultAESCBCCipher(keyB);

    String ciphertext = encryptCipher.encrypt("secret-value");

    expectedException.expect(JsonDBException.class);
    expectedException.expectMessage("DefaultAESCBCCipher failed to decrypt text");
    decryptCipher.decrypt(ciphertext);
  }

  @Test
  public void testDefaultAESCBCCipherByteArrayConstructor() throws GeneralSecurityException {
    byte[] keyBytes = java.util.Base64.getDecoder().decode("1r8+24pibarAWgS85/Heeg==");
    DefaultAESCBCCipher cipher = new DefaultAESCBCCipher(keyBytes, "UTF-8");

    String encrypted = cipher.encrypt("byte-key-constructor");
    assertEquals("byte-key-constructor", cipher.decrypt(encrypted));
  }

  @Test
  public void testDefault1DecryptCorruptedCiphertext() throws Exception {
    String key = CryptoUtil.generate128BitKey("MyPassword", "ksdfkja923u4anf");
    Default1Cipher cipher = new Default1Cipher(key);
    String ciphertext = cipher.encrypt("payload");
    String corrupted = ciphertext.substring(0, ciphertext.length() - 4) + "XXXX";

    expectedException.expect(JsonDBException.class);
    expectedException.expectMessage("Incorrect key for this ciphertext");
    cipher.decrypt(corrupted);
  }

  @Test
  public void testDefault1ByteArrayConstructor() throws Exception {
    byte[] keyBytes = java.util.Base64.getDecoder().decode(
        CryptoUtil.generate128BitKey("MyPassword", "ksdfkja923u4anf"));
    Default1Cipher cipher = new Default1Cipher(keyBytes);

    String encrypted = cipher.encrypt("byte-array-key");
    assertEquals("byte-array-key", cipher.decrypt(encrypted));
  }

  @Test
  public void testKeyDefault1() throws UnsupportedEncodingException, GeneralSecurityException {
    String base64EncodedKey = CryptoUtil.generate128BitKey("MyPassword", "ksdfkja923u4anf");
    ICipher cipher = new Default1Cipher(base64EncodedKey);
    
    String msg = "Hallo, gru\u00DF";
    
    String encryptedText = cipher.encrypt(msg);
    String decryptedText = cipher.decrypt(encryptedText);
    assertEquals(msg, decryptedText);
    assertTrue("ciphertext too short", encryptedText.length() > 40);
  }
}
