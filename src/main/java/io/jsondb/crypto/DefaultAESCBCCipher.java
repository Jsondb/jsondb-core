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
package io.jsondb.crypto;

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

import io.jsondb.JsonDBException;

/**
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class DefaultAESCBCCipher implements ICipher {
  private Logger logger = LoggerFactory.getLogger(DefaultAESCBCCipher.class);

  private static final String AES_ENCRYPTION_ALGORITHM = "AES";
  
  private final String charset;

  private Cipher encryptCipher;
  private Cipher decryptCipher;
  private ReentrantLock encryptionLock;
  private ReentrantLock decryptionLock;

  /**
   * This constructor assumes data to be of type 'UTF-8'
   * 
   * A default AES (CBC Mode) Cipher. AES is a 128-bit block cipher supporting keys of 128, 192, and 256 bits.
   * CBC may not be the most secure mode, maybe you want to use the AEAD mode. This implementation is just a 
   * example you can create your own implementation similar to this one using AES AEAD or entirely a different
   * crypto algorithm like say DES
   * 
   * Imp Note: This constructor takes a Base64 Encoded encryption key of type String, provided only for ease of use.
   *           Generally for such type of data a good practice is to use char[] so that it can be explicitly wiped of,
   *           Strings are immutable so it is possible that some other process may dump this process memory and locate
   *           the key, so yes this is a security concern. So if you wish you can create your own implementation of
   *           {@link io.jsondb.crypto.ICipher} that accepts a char[] or byte[] as key and you take care of filling
   *           up that array with garbage when done.
   * 
   * This constructor does not check if the given bytes indeed specify a secret key of the specified algorithm.
   * For example, this constructor does not check if key is 128, 192, 256 bytes long, and also does not check for weak or semi-weak keys.
   * 
   * Note: If you want to use Key &gt; 128 bits then you need to install Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction 
   *           Policy files.
   *
   * @param base64CodedEncryptionKey  A base 64 encoded symmetric key to be used during encryption and decryption.
   * @throws GeneralSecurityException  a general security exception
   */
  public DefaultAESCBCCipher(String base64CodedEncryptionKey) throws GeneralSecurityException {
    this(Base64.getDecoder().decode(base64CodedEncryptionKey), "UTF-8");
  }

  /**
   * A default AES (CBC Mode) Cipher. AES is a 128-bit block cipher supporting keys of 128, 192, and 256 bits.
   * CBC may not be the most secure mode, maybe you want to use the AEAD mode. This implementation is just a 
   * example you can create your own implementation similar to this one using AES AEAD or entirely a different
   * crypto algorithm like say DES
   * 
   * Imp Note: This constructor takes a Base64 Encoded encryption key of type String, provided only for ease of use.
   *           Generally for such type of data a good practice is to use char[] so that it can be explicitly wiped of,
   *           Strings are immutable so it is possible that some other process may dump this process memory and locate
   *           the key, so yes this is a security concern. So if you wish you can create your own implementation of
   *           {@link io.jsondb.crypto.ICipher} that accepts a char[] or byte[] as key and you take care of filling
   *           up that array with garbage when done.
   * 
   * This constructor does not check if the given bytes indeed specify a secret key of the specified algorithm.
   * For example, this constructor does not check if key is 128, 192, 256 bytes long, and also does not check for weak or semi-weak keys.
   * 
   * Note: If you want to use Key &gt; 128 bits then you need to install Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction 
   *           Policy files.
   *
   * @param base64CodedEncryptionKey  A base 64 encoded symmetric key to be used during encryption and decryption.
   * @param charset charset to be considered when encrypting plaintext, or decrypting cipher text
   * @throws GeneralSecurityException  a general security exception
   */
  public DefaultAESCBCCipher(String base64CodedEncryptionKey, String charset) throws GeneralSecurityException {
    this(Base64.getDecoder().decode(base64CodedEncryptionKey), charset);
  }
  
  /**
   * A default AES (CBC Mode) Cipher. AES is a 128-bit block cipher supporting keys of 128, 192, and 256 bits.
   * CBC may not be the most secure mode, maybe you want to use the AEAD mode. This implementation is just a 
   * example you can create your own implementation similar to this one using AES AEAD or entirely a different
   * crypto algorithm like say DES
   * 
   * This constructor does not check if the given bytes indeed specify a secret key of the specified algorithm.
   * For example, this constructor does not check if key is 128, 192, 256 bytes long, and also does not check for weak or semi-weak keys.
   * 
   * Note: If you want to use Key &gt; 128 bits then you need to install Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction 
   *           Policy files.
   *
   * @param encryptionKey  symmetric key to be used during encryption or decryption.
   * @param charset charset to be considered when encrypting plaintext, or decrypting cipher text
   * @throws GeneralSecurityException  a general security exception
   */
  public DefaultAESCBCCipher(byte[] encryptionKey, String charset) throws GeneralSecurityException {
    this.charset = charset;

    encryptionLock = new ReentrantLock();
    decryptionLock = new ReentrantLock();
    try {
      this.encryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
      this.decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
      SecretKeySpec key = new SecretKeySpec(encryptionKey, AES_ENCRYPTION_ALGORITHM);
      encryptCipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(encryptionKey));
      decryptCipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(encryptionKey));
    } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException e) {
      logger.error("Failed to create DefaultAESCBCCipher", e);
      throw e;
    } catch (InvalidKeyException e) {
      logger.error("Failed to create DefaultAESCBCCipher", e);
      throw new InvalidKeyException("Failed to create DefaultAESCBCCipher, something wrong with the key");
    } catch (InvalidAlgorithmParameterException e) {
      logger.error("Failed to create DefaultAESCBCCipher", e);
      throw new InvalidKeyException("Failed to create DefaultAESCBCCipher, something wrong with the key");
    }
  }

  /**
   * This method is used to encrypt(Symmetric) plainText coming in input using AES algorithm
   * @param plainText the plain text string to be encrypted
   * @return Base64 encoded AES encrypted cipher text
   */
  @Override
  public String encrypt(String plainText) {
    this.encryptionLock.lock();
    try{
      byte[] cipherBytes = null;
      try {
        cipherBytes = encryptCipher.doFinal(plainText.getBytes(charset));
      } catch (UnsupportedEncodingException e) {
        logger.error("DefaultAESCBCCipher failed to encrypt text", e);
        throw new JsonDBException("DefaultAESCBCCipher failed to encrypt text", e);
      } catch (IllegalBlockSizeException e) {
        logger.error("DefaultAESCBCCipher failed to encrypt text", e);
        throw new JsonDBException("DefaultAESCBCCipher failed to encrypt text", e);
      } catch (BadPaddingException e) {
        logger.error("DefaultAESCBCCipher failed to encrypt text", e);
        throw new JsonDBException("DefaultAESCBCCipher failed to encrypt text", e);
      }
      return Base64.getEncoder().encodeToString(cipherBytes);
    } finally{
      this.encryptionLock.unlock();
    }

  }

  /**
   * A method to decrypt the provided cipher text.
   *
   * @param cipherText AES encrypted cipherText
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
        logger.error("DefaultAESCBCCipher failed to decrypt text", e);
        throw new JsonDBException("DefaultAESCBCCipher failed to decrypt text", e);
      } catch (IllegalBlockSizeException e) {
        logger.error("DefaultAESCBCCipher failed to decrypt text", e);
        throw new JsonDBException("DefaultAESCBCCipher failed to decrypt text", e);
      } catch (BadPaddingException e) {
        logger.error("DefaultAESCBCCipher failed to decrypt text", e);
        throw new JsonDBException("DefaultAESCBCCipher failed to decrypt text", e);
      }
      return decryptedValue;
    } finally{
      this.decryptionLock.unlock();
    }
  }
}
