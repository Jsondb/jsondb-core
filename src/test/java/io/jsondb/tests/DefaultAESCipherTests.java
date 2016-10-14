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

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.jsondb.crypto.CryptoUtil;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;

/**
 * @version 1.0 08-Oct-2016
 */
public class DefaultAESCipherTests {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  
  @Test
  public void testKeyValidity() throws UnsupportedEncodingException, GeneralSecurityException {
    expectedException.expect(InvalidKeyException.class);
    expectedException.expectMessage("Failed to create DefaultAESCBCCipher, something wrong with the key");
    
    @SuppressWarnings("unused")
    DefaultAESCBCCipher cipher = new DefaultAESCBCCipher("badkey", "UTF-8");
  }
  
  @Test
  public void testKey() throws UnsupportedEncodingException, GeneralSecurityException {
    String base64EncodedKey = CryptoUtil.generate128BitKey("MyPassword", "ksdfkja923u4anf");
    
    ICipher cipher = new DefaultAESCBCCipher(base64EncodedKey);
    
    String encryptedText = cipher.encrypt("Hallo, Wie gehts");
    assertEquals("cEIxZFDVSlWIN+ZmOGyvmbWv4+ziI884BNu0Hplglws=", encryptedText);
    String decryptedText = cipher.decrypt(encryptedText);
    assertEquals("Hallo, Wie gehts", decryptedText);
  }
}
