/*
 * Copyright (c) 2019 Reinier Zwitserloot
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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.jsondb.JsonDBException;

/**
 * A default AES (GCM Mode) Cipher. AES is a 128-bit block cipher supporting keys of 128, 192, and 256 bits.
 * 
 * The constructors do not check if key provided as parameter indeed specifies a valid AES key. It does not check key size,
 * nor does it check for weak or sem-weak keys.
 * 
 * Note: If you want to use Key &gt; 128 bits then you need to install Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction 
 *       Policy files.
 *
 * @author Reinier Zwitserloot
 * @version 1.0 20-Jun-2019
 */
public class Default1Cipher implements ICipher {
  private static final String ENCRYPTION_ALGORITHM = "AES";
  private static final String MODE_ALGORITHM = "GCM";
  private static final String PADDING_ALGORITHM = "NoPadding";
  private static final String PROVIDER = "SunJCE";
  private static final String CIPHER_ALGORITHM = ENCRYPTION_ALGORITHM + "/" + MODE_ALGORITHM + "/" + PADDING_ALGORITHM;
  private static final int IV_SIZE = 16;
  private static final int TAG_SIZE = 128;
  
  /* intentionally letting the system pick a sane SecureRandom; SecureRandom.getInstanceStrong() can block and is overkill. */
  private static final SecureRandom rnd = createNewSecureRandom();
  
  private static final SecureRandom createNewSecureRandom() {
    try {
      return SecureRandom.getInstance("NativePRNGNonBlocking");
    } catch (NoSuchAlgorithmException e) {
      try {
        return SecureRandom.getInstance("SHA1PRNG");
      } catch (NoSuchAlgorithmException e2) {
        return new SecureRandom();
      }
    }
  }

  private final Charset charset;
  private final SecretKeySpec key;

  /**
   * Creates a new default cipher using 'UTF-8' encoding, with a base64-encoded key.
   * 
   * @param base64CodedEncryptionKey  A base 64 encoded symmetric key to be used during encryption and decryption.
   */
  public Default1Cipher(String base64CodedEncryptionKey) throws GeneralSecurityException {
    this(Base64.getDecoder().decode(base64CodedEncryptionKey), StandardCharsets.UTF_8);
  }

  /**
   * Creates a new default cipher using 'UTF-8' encoding and key.
   * 
   * @param encryptionKey  A symmetric key to be used during encryption and decryption.
   */
  public Default1Cipher(byte[] encryptionKey) throws GeneralSecurityException {
    this(encryptionKey, StandardCharsets.UTF_8);
  }

  /**
   * Creates a new default cipher with the specified charset encoding, with a base64-encoded key.
   * 
   * @param base64CodedEncryptionKey  A base 64 encoded symmetric key to be used during encryption and decryption.
   * @param charset                   The charset to be considered when encrypting plaintext or decrypting ciphertext.
   */
  public Default1Cipher(String base64CodedEncryptionKey, Charset charset) throws GeneralSecurityException {
    this(Base64.getDecoder().decode(base64CodedEncryptionKey), charset);
  }

  /**
   * Creates a new default cipher with the specified charset encoding and key.
   * 
   * @param encryptionKey  A symmetric key to be used during encryption and decryption.
   * @param charset        The charset to be considered when encrypting plaintext or decrypting ciphertext.
   */
  public Default1Cipher(byte[] encryptionKey, Charset charset) throws GeneralSecurityException {
    if (charset == null) throw new NullPointerException("charset");
    if (encryptionKey == null) throw new NullPointerException("encryptionKey");
    this.charset = charset;
    this.key = new SecretKeySpec(encryptionKey, ENCRYPTION_ALGORITHM);
  }

  /** @{inheritDoc} */
  @Override
  public String encrypt(String plainText) {
    try {
      byte[] iv = new byte[IV_SIZE];
      rnd.nextBytes(iv);
      Cipher enc = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
      enc.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
      byte[] input = plainText.getBytes(charset);
      int sizeReq = IV_SIZE + enc.getOutputSize(input.length);
      byte[] output = new byte[sizeReq];
      ByteBuffer store = ByteBuffer.wrap(output);
      store.put(iv);
      int extra = enc.doFinal(input, 0, input.length, output, IV_SIZE);
      store.position(store.position() + extra);
      store.flip();
      ByteBuffer bb = Base64.getEncoder().encode(store);
      return new String(bb.array(), 0, bb.limit(), StandardCharsets.US_ASCII);
    } catch (NoSuchPaddingException | NoSuchProviderException | NoSuchAlgorithmException | IllegalBlockSizeException |InvalidAlgorithmParameterException e) {
      throw new JsonDBException("Default cipher cannot be used on this VM installation", e);
    } catch (InvalidKeyException e) {
      throw new JsonDBException("Invalid key", e);
    } catch (BadPaddingException | ShortBufferException e) {
      throw new JsonDBException("Unexpected (bug?) crypto error", e);
    }
  }

  /** @{inheritDoc} */
  @Override
  public String decrypt(String cipherText) {
    byte[] in = Base64.getDecoder().decode(cipherText);
    try {
      Cipher dec = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
      dec.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, in, 0, IV_SIZE));
      return new String(dec.doFinal(in, IV_SIZE, in.length - IV_SIZE), charset);
    } catch (NoSuchPaddingException | NoSuchProviderException | NoSuchAlgorithmException | IllegalBlockSizeException e) {
      throw new JsonDBException("Default cipher cannot be used on this VM installation", e);
    } catch (InvalidKeyException e) {
      throw new JsonDBException("Invalid key", e);
    } catch (AEADBadTagException e) {
      throw new JsonDBException("Incorrect key for this ciphertext (or ciphertext is corrupted)", e);
    } catch (BadPaddingException | InvalidAlgorithmParameterException e) {
      throw new JsonDBException("Unexpected (bug?) crypto error", e);
    }
  }
}
