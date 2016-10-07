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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
   */
  public static void encryptFields(Object object, CollectionMetaData cmd, ICipher cipher) {
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
      } catch (IllegalAccessException e) {
        logger.error("Failed to invoke method for a secretAnnotated field due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke method for a secretAnnotated field due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke method for a secretAnnotated field, the method threw a exception", e);
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
   */
  public static void decryptFields(Object object, CollectionMetaData cmd, ICipher cipher) {

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
      } catch (IllegalAccessException e) {
        logger.error("Failed to invoke method for a secretAnnotated field due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke method for a secretAnnotated field due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke method for a secretAnnotated field, the method threw a exception", e);
      }
    }
  }
}
