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
import java.text.ParseException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
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
  private static ObjectMapper objectMapper = new ObjectMapper()
          .registerModule(new ParameterNamesModule())
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule());
  
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
        logger.error("Failed to invoke getter method for a idAnnotated field due to permissions", e);
        throw new InvalidJsonDbApiUsageException("Failed to invoke getter method for a idAnnotated field due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke getter method for a idAnnotated field due to wrong arguments", e);
        throw new InvalidJsonDbApiUsageException("Failed to invoke getter method for a idAnnotated field due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke getter method for a idAnnotated field, the method threw a exception", e);
        throw new InvalidJsonDbApiUsageException("Failed to invoke getter method for a idAnnotated field, the method threw a exception", e);
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
        logger.error("Failed to invoke setter method for a idAnnotated field due to permissions", e);
        throw new InvalidJsonDbApiUsageException("Failed to invoke setter method for a idAnnotated field due to permissions", e);
      } catch (IllegalArgumentException e) {
        logger.error("Failed to invoke setter method for a idAnnotated field due to wrong arguments", e);
        throw new InvalidJsonDbApiUsageException("Failed to invoke setter method for a idAnnotated field due to wrong arguments", e);
      } catch (InvocationTargetException e) {
        logger.error("Failed to invoke setter method for a idAnnotated field, the method threw a exception", e);
        throw new InvalidJsonDbApiUsageException("Failed to invoke setter method for a idAnnotated field, the method threw a exception", e);
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
    try {
      if (fromBean != null) {
        return objectMapper.readValue(objectMapper.writeValueAsString(fromBean), fromBean.getClass());
      }
      return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  /**
   * A utility method to determine the default value to be assigned to i  when it is not specified based on the value of k
   * If i is not given it defaults to 0 for k&gt;0 and n-1 for k&lt;0, where n is number elements in slice_target.
   *
   * @param k actual value of k parsed from slice string
   * @param n number of elements in slice_target
   * @return default value for i
   */
  private static int getDefaultIByK(int k, int n) {
    if (k > 0) {
      return 0;
    } else if ( k < 0) {
      return n-1;
    } else {
      throw new IllegalArgumentException("Illegal argument, expected k != 0");
    }
  }

  /**
   * A utility method to determine the default value to be assigned to j when it is not specified based on the value of k
   * If j is not given it defaults to n for k&gt;0 and -n-1 for k&lt;0, where n is number elements in slice_target.
   *
   * @param k actual value of k parsed from slice string
   * @param n number of elements in slice_target
   * @return default value for j
   */
  private static int getDefaultJByK(int k, int n) {
    if (k > 0) {
      return n;
    } else if ( k < 0) {
      return -n-1;
    } else {
      throw new IllegalArgumentException("Illegal argument, expected k != 0");
    }
  }

  /**
   * A private utility method used to parse the i|j from part of slice string. If i or j is negative
   * it is adjusted to n+i or n+j.
   * where n is number elements in slice_target
   *
   * @param part a non-null non-empty integer part string
   * @param n number of elements in slice_target
   * @return the parsed integer value, adjusted appropriately if its negative
   * @throws IllegalArgumentException a exception of the part string is not a valid number
   */
  private static int parsePartIJ(String part, int n) throws IllegalArgumentException {
    part = part.trim();
    if (part.length() > 0) {
      try {
        int p = Integer.parseInt(part);
        if (p < 0) {
          p += n;
        }
        return p;
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Illegal slice argument, expected a integer representing i in i:j:k");
      }
    } else {
      throw new IllegalArgumentException("Illegal slice argument, expected a integer representing i in i:j:k");
    }
  }

  /**
   * A private utility method used to parse the k from part of slice string.
   *
   * @param part a non-null integer part string
   * @param def value to assign to k if none can be parsed out
   * @return the parsed or the default k value
   * @throws IllegalArgumentException a exception of the part string is not a valid number
   */
  private static int parsePartK(String part, int def) throws IllegalArgumentException {
    part = part.trim();
    if (part.length() > 0) {
      try {
        return Integer.parseInt(part);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Illegal slice argument, expected format is i:j:k");
      }
    }
    return def;
  }

  public static boolean isSliceable(String slice) {
    if (slice == null || slice.length() < 1 || slice.equals(":") || slice.equals("::")) {
      return false;
    }
    return true;
  }

  /**
   * Utility method to compute the indexes to select based on slice string
   * @param slice select the indices in some slice_target list or array, should be a valid slice string
   *
   *              The behaviour of this slicing feature is similar to
   *              the slicing feature in python or numpy, as much as possible
   *              https://docs.scipy.org/doc/numpy-1.13.0/reference/arrays.indexing.html
   *
   *              A slice is a string notation and the basic slice syntax is i:j:k, where i is the starting index,
   *              j is the stopping index, and k is the step (k != 0). In other words it is start:stop:step
   *              Example slice_target = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "1:7:2" returns [T1, T3, T5]
   *
   *              i is inclusive, j is exclusive
   *
   *              Negative i and j are interpreted as n + i and n + j where n is the number of elements found. Negative
   *              k makes stepping go towards smaller indices.
   *              Example slice_target = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "-2:10" returns [T8, T9]
   *                      slice = "-3:3:-1" returns [T7, T6, T5, T4]
   *
   *              Assume n is the number of elements in slice_target. Then, if i is not given it defaults to 0
   *              for k&gt;0 and n - 1 for k&lt;0 . If j is not given it defaults to n for k&gt;0 and -n-1 for k&lt;0 .
   *              If k is not given it defaults to 1. Note that :: is the same as : and means select all indices
   *              from slice_target.
   *              Example slice_target = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "5:" returns [T5, T6, T7, T8, T9]
   *
   *              Assume n is the number of elements in slice_target. Then, if j&gt;n then it is considered as n, in case
   *              of negative j it is considered -n.
   * @param size size of the array from which a slice is to extracted
   * @return List of indexes to pick from the array to be returned
   */
  public static List<Integer> getSliceIndexes(String slice, int size) {

    if (!isSliceable(slice) || size < 1) {
      return null;
    }

    int i=0,j=0,k = 0;

    String[] parts = slice.split(":");
    if (parts.length > 3) {
      throw new IllegalArgumentException("Illegal slice argument, expected format is i:j:k");
    }
    if (slice.startsWith("::")) {
      k = parsePartK(parts[2], 1);
      i = getDefaultIByK(k, size);
      j = getDefaultJByK(k, size);
    } else if (slice.startsWith(":")) {
      if (parts.length == 2) {
        j = parsePartIJ(parts[1], size);
        k = 1;
        i = getDefaultIByK(k, size);
      } else if (parts.length == 3) {
        j = parsePartIJ(parts[1], size);
        k = parsePartK(parts[2], 1);
        i = getDefaultIByK(k, size);
      }
    } else {
      if (parts.length == 1) {
        i = parsePartIJ(parts[0], size);
        k = 1;
        j = getDefaultJByK(k, size);
      } else if (parts.length == 2) {
        i = parsePartIJ(parts[0], size);
        j = parsePartIJ(parts[1], size);
        k = 1;
      } else if (parts.length == 3) {
        k = parsePartK(parts[2], 1);
        if (parts[0].length() > 0) {
          i = parsePartIJ(parts[0], size);
        } else {
          i = getDefaultIByK(k, size);
        }
        if (parts[1].length() > 0) {
          j = parsePartIJ(parts[1], size);
        } else {
          j = getDefaultJByK(k, size);
        }
      }
    }

    List<Integer> indexes = new ArrayList<>();
    if (k == 0) {
      throw new IllegalArgumentException("Illegal slice argument, k cannot be zero");
    } else if (k > 0) {
      int m = i;
      int n = j;
      while (m < n && m < size) { //Second condition prevents ArrayIndexOutOfBounds
        indexes.add(m);
        m += k;
      }
    } else if (k < 0) {
      int m = i;
      int n = j;
      while (m > n && m > -1) { //Second condition prevents ArrayIndexOutOfBounds
        indexes.add(m);
        m += k;
      }
    }
    return indexes;
  }
}
