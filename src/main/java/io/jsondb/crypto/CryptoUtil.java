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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsondb.CollectionMetaData;

/**
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class CryptoUtil {
  private static Logger logger = LoggerFactory.getLogger(CryptoUtil.class);

  /**
   * A utility method to encrypt the value of field marked by the @Secret annotation using its
   * setter/mutator method.
   * @param object the actual Object representing the POJO we want the Id of.
   * @param cmd the CollectionMetaData object from which we can obtain the list
   *        containing names of fields which have the @Secret annotation
   * @param cipher the actual cipher implementation to use
   * @throws IllegalAccessException Error when invoking method for a @Secret annotated field due to permissions
   * @throws IllegalArgumentException Error when invoking method for a @Secret annotated field due to wrong arguments
   * @throws InvocationTargetException Error when invoking method for a @Secret annotated field, the method threw a exception
   */
  public static void encryptFields(Object object, CollectionMetaData cmd, ICipher cipher) 
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    for (String secretAnnotatedFieldName: cmd.getSecretAnnotatedFieldNames()) {
      Method getterMethod = cmd.getGetterMethodForFieldName(secretAnnotatedFieldName);
      Method setterMethod = cmd.getSetterMethodForFieldName(secretAnnotatedFieldName);

      String value;
      String encryptedValue = null;
      try {
        value = (String)getterMethod.invoke(object);
        if (null != value) {
          encryptedValue = cipher.encrypt(value);
          setterMethod.invoke(object, encryptedValue);
        }
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        logger.error("Error when invoking method for a @Secret annotated field", e);
        throw e;
      }
    }
  }

  /**
   * A utility method to decrypt the value of field marked by the @Secret annotation using its
   * setter/mutator method.
   * @param object the actual Object representing the POJO we want the Id of.
   * @param cmd the CollectionMetaData object from which we can obtain the list
   *        containing names of fields which have the @Secret annotation
   * @param cipher the actual cipher implementation to use
   * @throws IllegalAccessException Error when invoking method for a @Secret annotated field due to permissions
   * @throws IllegalArgumentException Error when invoking method for a @Secret annotated field due to wrong arguments
   * @throws InvocationTargetException Error when invoking method for a @Secret annotated field, the method threw a exception
   */
  public static void decryptFields(Object object, CollectionMetaData cmd, ICipher cipher) 
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {

    for (String secretAnnotatedFieldName: cmd.getSecretAnnotatedFieldNames()) {
      Method getterMethod = cmd.getGetterMethodForFieldName(secretAnnotatedFieldName);
      Method setterMethod = cmd.getSetterMethodForFieldName(secretAnnotatedFieldName);

      String value;
      String decryptedValue = null;
      try {
        value = (String)getterMethod.invoke(object);
        if (null != value) {
          decryptedValue = cipher.decrypt(value);
          setterMethod.invoke(object, decryptedValue);
        }
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        logger.error("Error when invoking method for a @Secret annotated field", e);
        throw e;
      }
    }
  }
  
  /**
   * Utility method to help generate a strong 128 bit Key to be used for the DefaultAESCBCCipher.
   * 
   * Note: This function is only provided as a utility to generate strong password it should
   *       be used statically to generate a key and then the key should be captured and used in your program.
   *
   * This function defaults to using 65536 iterations and 128 bits for key size. If you wish to use 256 bits key size
   * then ensure that you have Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy installed 
   * and change the last argument to {@link javax.crypto.spec.PBEKeySpec} below to 256
   *
   * @param password A password which acts as a seed for generation of the key
   * @param salt A salt used in combination with the password for the generation of the key
   * 
   * @return A Base64 Encoded string representing the 128 bit key
   * 
   * @throws NoSuchAlgorithmException if the KeyFactory algorithm is not found in available crypto providers
   * @throws UnsupportedEncodingException if the char encoding of the salt is not known.
   * @throws InvalidKeySpecException invalid generated key
   */
  public static String generate128BitKey(String password, String salt) 
      throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeySpecException {

    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 128);
    SecretKey key = factory.generateSecret(spec);
    byte[] keybyte = key.getEncoded();
    return Base64.getEncoder().encodeToString(keybyte);
  }
}
