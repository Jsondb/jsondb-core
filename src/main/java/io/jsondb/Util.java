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
package io.jsondb;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.jsondb.annotation.Document;

/**
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class Util {
  private static Logger logger = LoggerFactory.getLogger(Util.class);

  private static final Collection<String> RESTRICTED_CLASSES;
  static {

    Set<String> restrictedClasses = new HashSet<String>();
    restrictedClasses.add(List.class.getName());
    restrictedClasses.add(Collection.class.getName());
    restrictedClasses.add(Iterator.class.getName());
    restrictedClasses.add(HashSet.class.getName());

    RESTRICTED_CLASSES = Collections.unmodifiableCollection(restrictedClasses);
  }
  
  protected static void ensureNotRestricted(Object o) {
    if (o.getClass().isArray() || RESTRICTED_CLASSES.contains(o.getClass().getName())) {
      throw new InvalidJsonDbApiUsageException("Collection object cannot be inserted, removed, updated or upserted as a single object");
    }
  }
  
  protected static <T> String determineEntityCollectionName(T obj) {
    return determineCollectionName(obj.getClass());
  }

  /**
   * A utility method to determine the collection name for a given entity class.
   * This method attempts to find a the annotation {@link io.jsondb.annotation.Document} on this class.
   * If found then we know the collection name else it throws a exception
   * @param entityClass  class that determines the name of the collection
   * @return  name of the class 
   */
  protected static String determineCollectionName(Class<?> entityClass) {
    if (entityClass == null) {
        throw new InvalidJsonDbApiUsageException(
              "No class parameter provided, entity collection can't be determined");
    }
    Document doc = entityClass.getAnnotation(Document.class);
    if (null == doc) {
      throw new InvalidJsonDbApiUsageException(
          "Entity '" + entityClass.getSimpleName() + "' is not annotated with annotation @Document");
    }
    String collectionName = doc.collection();

    return collectionName;
  }
  
  /**
   * A utility method to extract the value of field marked by the @Id annotation using its
   * getter/accessor method.
   * @param document the actual Object representing the POJO we want the Id of.
   * @param getterMethodForId the Method that is the accessor for the attributed with @Id annotation
   * @return the actual Id or if none exists then a new random UUID
   */
  protected static Object getIdForEntity(Object document, Method getterMethodForId) {
    Object id = null;
    if (null != getterMethodForId) {
      try {
        id = getterMethodForId.invoke(document);
      } catch (IllegalAccessException e) {
        logger.error("Failed to invoke method for a idAnnotated field due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke method for a idAnnotated field due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke method for a idAnnotated field, the method threw a exception", e);
      }
    }
    return id;
  }

  /**
   * A utility method to set the value of field marked by the @Id annotation using its
   * setter/mutator method.
   * TODO: Some day we want to support policies for generation of ID like AutoIncrement etc.
   *
   * @param document the actual Object representing the POJO we want the Id to be set for.
   * @param setterMethodForId the Method that is the mutator for the attributed with @Id annotation
   * @return the Id that was generated and set
   */
  protected static Object setIdForEntity(Object document, Method setterMethodForId) {
    Object id = UUID.randomUUID().toString();
    if (null != setterMethodForId) {
      try {
        id = setterMethodForId.invoke(document, id);
      } catch (IllegalAccessException e) {
        logger.error("Failed to invoke method for a idAnnotated field due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke method for a idAnnotated field due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke method for a idAnnotated field, the method threw a exception", e);
      }
    }
    return id;
  }

  protected static Object setFieldValueForEntity(Object document, Object newValue, Method setterMethod) {
    Object retval = null;
    if (null != setterMethod) {
      try {
        retval = setterMethod.invoke(document, newValue);
      } catch (IllegalAccessException e) {
        logger.error("Failed to invoke method due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke method due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke method, the method threw a exception", e);
      }
    }
    return retval;
  }

  /**
   * A utility method that creates a deep clone of the specified object.
   * There is no other way of doing this reliably.
   *
   * @param fromBean java bean to be cloned.
   * @return a new java bean cloned from fromBean.
   */
  protected static Object deepCopy(Object fromBean) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    XMLEncoder out = new XMLEncoder(bos);
    out.writeObject(fromBean);
    out.close();

    ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
    XMLDecoder in = new XMLDecoder(bis, null, null, JsonDBTemplate.class.getClassLoader());
    Object toBean = in.readObject();
    in.close();
    return toBean;
  }

  /**
   * Utility to stamp the version into a newly created .json File
   * This method is expected to be invoked on a newly created .json file before it is usable.
   * So no locking code required.
   * 
   * @param dbConfig  all the settings used by Json DB
   * @param f  the target .json file on which to stamp the version
   * @param version  the actual version string to stamp
   * @return true if success.
   */
  public static boolean stampVersion(JsonDBConfig dbConfig, File f, String version) {

    FileOutputStream fos = null;
    OutputStreamWriter osr = null;
    BufferedWriter writer = null;
    try {
      fos = new FileOutputStream(f);
      osr = new OutputStreamWriter(fos, dbConfig.getCharset());
      writer = new BufferedWriter(osr);

      String versionData = dbConfig.getObjectMapper().writeValueAsString(new SchemaVersion(version));
      writer.write(versionData);
      writer.newLine();
    } catch (JsonProcessingException e) {
      logger.error("Failed to serialize SchemaVersion to Json string", e);
      return false;
    } catch (IOException e) {
      logger.error("Failed to write SchemaVersion to the new .json file {}", f, e);
      return false;
    } finally {
      try {
        writer.close();
      } catch (IOException e) {
        logger.error("Failed to close BufferedWriter for new collection file {}", f, e);
      }
      try {
        osr.close();
      } catch (IOException e) {
        logger.error("Failed to close OutputStreamWriter for new collection file {}", f, e);
      }
      try {
        fos.close();
      } catch (IOException e) {
        logger.error("Failed to close FileOutputStream for new collection file {}", f, e);
      }
    }
    return true;
  }

  /**
   * Utility to delete directory recursively
   * @param f  File object representing the directory to recursively delete
   */
  public static void delete(File f) {
    if (f.isDirectory()) {
      for (File c : f.listFiles())
        delete(c);
    }
    f.delete();
  }
}
