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
package org.jsondb.crypto;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class DefaultAESCipher implements ICipher {
  private Logger logger = LoggerFactory.getLogger(DefaultAESCipher.class);

  private final String encryptionKey;
  private final String charset;
  
  private Cipher encryptCipher;
  private Cipher decryptCipher;
  private ReentrantLock encryptionLock;
  private ReentrantLock decryptionLock;

  /**
   * Constructor that assumes data to be of type 'UTF-8'
   *
   * @param encryptionKey EncryptionKeySpec
   * @throws UnsupportedEncodingException
   * @throws GeneralSecurityException
   */
  public DefaultAESCipher(String encryptionKey) throws UnsupportedEncodingException, GeneralSecurityException {
    this(encryptionKey, "UTF-8");
  }

  public DefaultAESCipher(String encryptionKey, String charset) 
      throws GeneralSecurityException, UnsupportedEncodingException {
    super();
    this.encryptionKey = encryptionKey;
    this.charset = charset;

    encryptionLock = new ReentrantLock();
    decryptionLock = new ReentrantLock();
    try {
      this.encryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
      this.decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
      SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(charset), "AES");
      encryptCipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(encryptionKey.getBytes("UTF-8")));
      decryptCipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(encryptionKey.getBytes("UTF-8")));
    } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException e) {
      logger.error("Failed to create AESChiper", e);
      throw e;
    } catch (UnsupportedEncodingException e) {
      logger.error("Failed to create AESChiper", e);
      throw e;
    } catch (InvalidKeyException e) {
      logger.error("Failed to create AESChiper", e);
      throw e;
    } catch (InvalidAlgorithmParameterException e) {
      logger.error("Failed to create AESChiper", e);
      throw e;
    }
  }

  public String getEncryptionKey() {
    return encryptionKey;
  }

  /**
   * This method is used to encrypt(Symmetric) plainText coming in input using AES algorithm
   * @param plainText
   * @return Base64 encrypted AES encrypted cipher
   */
  @Override
  public String encrypt(String plainText) {
    this.encryptionLock.lock();
    try{
      byte[] cipherBytes = null;
      try {
        cipherBytes = encryptCipher.doFinal(plainText.getBytes(charset));
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IllegalBlockSizeException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (BadPaddingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return Base64.getEncoder().encodeToString(cipherBytes);
    } finally{
      this.encryptionLock.unlock();
    }

  }

  /**
   *
   * @param Base64 encrypted AES encrypted cipherText
   * @return decrypted text
   */
  @Override
  public String decrypt(String cipherText) {
    this.decryptionLock.lock();
    try{
      String decryptedValue = null;
      try {
        byte[] bytes = Base64.getDecoder().decode(cipherText);
        decryptedValue = new String(decryptCipher.doFinal(bytes), charset);
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IllegalBlockSizeException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (BadPaddingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return decryptedValue;
    } finally{
      this.decryptionLock.unlock();
    }
  }
}
