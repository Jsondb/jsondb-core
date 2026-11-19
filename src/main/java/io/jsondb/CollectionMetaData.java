/*
 * Copyright (c) 2016 - 2026 Farooq Khan
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.reflections.Reflections;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;
import io.jsondb.annotation.Secret;

/**
 * @version 1.0 25-Sep-2016
 */
public class CollectionMetaData {
  private String collectionName;
  private String schemaVersion;
  private String actualSchemaVersion;
  private Comparator<String> schemaComparator;
  private Class<?> clazz;

  private String idAnnotatedFieldName;
  private Class<?> idAnnotatedFieldType;
  private Method idAnnotatedFieldGetterMethod;
  private Method idAnnotatedFieldSetterMethod;

  private final ReentrantReadWriteLock collectionLock;

  private List<String> secretAnnotatedFieldNames = new ArrayList<String>();
  private Map<String, Method> getterMethodMap = new TreeMap<String, Method>();
  private Map<String, Method> setterMethodMap = new TreeMap<String, Method>();

  private boolean hasSecret;
  private boolean readonly;

  public CollectionMetaData(String collectionName, Class<?> clazz, String schemaVersion, Comparator<String> schemaComparator) {
    super();
    this.collectionName = collectionName;
    this.schemaVersion = schemaVersion;
    this.schemaComparator = schemaComparator;
    this.clazz = clazz;

    this.collectionLock = new ReentrantReadWriteLock();

    //Populate the class metadata
    setupClassMetadata(clazz);
    if (null == idAnnotatedFieldName) {
      // Another framework's @Id, e.g. javax.persistence.Id, is the usual cause (issue #23)
      throw new InvalidJsonDbApiUsageException("Collection class " + clazz.getName()
          + " has no field annotated with @io.jsondb.annotation.Id; every @Document class needs one."
          + " Other @Id annotations such as javax.persistence.Id are not recognised.");
    }

    //Without java.beans (Android) JXPath cannot introspect the class, so queries need this handler
    ReflectionPropertyHandler.registerIfJavaBeansMissing(clazz);

    this.idAnnotatedFieldGetterMethod = getterMethodMap.get(idAnnotatedFieldName);
    this.idAnnotatedFieldSetterMethod = setterMethodMap.get(idAnnotatedFieldName);
  }

  private void setupClassMetadata(final Class<?> clazz) {
    Field[] fs = clazz.getDeclaredFields();
    Method[] ms = clazz.getDeclaredMethods();
    for (Field f : fs) {
      String fieldName = f.getName();

      Annotation[] annotations = f.getDeclaredAnnotations();
      for (Annotation a : annotations) {
        if (a.annotationType().equals(Id.class)) {
          //We expect only one @Id annotated field and only one corresponding getter for it
          //This logic will capture the last @Id annotated field if there are more than one.
          this.idAnnotatedFieldName = fieldName;
          this.idAnnotatedFieldType = f.getType();
        }
        if (a.annotationType().equals(Secret.class)) {
          this.secretAnnotatedFieldNames.add(fieldName);
          this.hasSecret = true;
        }
      }

      String getterMethodName = formGetterMethodName(f);
      String setterMethodName = formSetterMethodName(f);
      for (Method m : ms) {
        if (m.getName().equals(getterMethodName)) {
          this.getterMethodMap.put(fieldName, m);
        }
        if (m.getName().equals(setterMethodName)) {
          this.setterMethodMap.put(fieldName, m);
        }
      }
    }
    if (clazz.getSuperclass() != Object.class) setupClassMetadata(clazz.getSuperclass());
  }

  protected ReentrantReadWriteLock getCollectionLock() {
    return collectionLock;
  }

  public String getCollectionName() {
    return collectionName;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public String getActualSchemaVersion() {
    return actualSchemaVersion;
  }

  public void setActualSchemaVersion(String actualSchemaVersion) {
    this.actualSchemaVersion = actualSchemaVersion;
    int compareResult = schemaComparator.compare(schemaVersion, actualSchemaVersion);
    if (compareResult != 0) {
      readonly = true;
    } else {
      readonly = false;
    }
  }

  @SuppressWarnings("rawtypes")
  public Class getClazz() {
    return clazz;
  }

  public String getIdAnnotatedFieldName() {
    return idAnnotatedFieldName;
  }

  /**
   * @return the declared type of the @Id field, e.g. String, Long or int
   */
  public Class<?> getIdAnnotatedFieldType() {
    return idAnnotatedFieldType;
  }

  public Method getIdAnnotatedFieldGetterMethod() {
    return idAnnotatedFieldGetterMethod;
  }

  public Method getIdAnnotatedFieldSetterMethod() {
    return idAnnotatedFieldSetterMethod;
  }

  public List<String> getSecretAnnotatedFieldNames() {
    return secretAnnotatedFieldNames;
  }

  public boolean isSecretField(String fieldName) {
    return secretAnnotatedFieldNames.contains(fieldName);
  }

  public Method getGetterMethodForFieldName(String fieldName) {
    return getterMethodMap.get(fieldName);
  }

  public Method getSetterMethodForFieldName(String fieldName) {
    return setterMethodMap.get(fieldName);
  }

  public boolean hasSecret() {
    return hasSecret;
  }

  public boolean isReadOnly() {
    return readonly;
  }

  private String formGetterMethodName(Field field) {
    String fieldName = field.getName();
    if (field.getType().equals(boolean.class)) {
      return "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    } else {
      return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
  }

  private String formSetterMethodName(Field field) {
    String fieldName = field.getName();
    return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
  }

/*  @SuppressWarnings("unused")
  private String formFieldName(String methodName) {
    if(methodName.startsWith("get") || methodName.startsWith("set")) {
      String fName = methodName.substring(3);
      return Character.toLowerCase(fName.charAt(0)) + fName.substring(1);
    } else if (methodName.startsWith("is")) {
      String fName = methodName.substring(2);
      return Character.toLowerCase(fName.charAt(0)) + fName.substring(1);
    }
    //Method has not be named according to Java Bean Specifications. No way to figure out.
    return null;
  }*/

  /**
   * A utility builder method that builds the metadata for every collection class/POJO. The classes
   * are the explicitly registered entity classes when the config has them, otherwise all classes
   * annotated with the @Document annotation found by scanning the baseScanPackage.
   *
   * @param dbConfig the object that holds the entity classes or baseScanPackage and other settings.
   * @return A Map of collection classes/POJOs
   */
  public static Map<String, CollectionMetaData> builder(JsonDBConfig dbConfig) {
    Collection<Class<?>> entityClasses = dbConfig.getEntityClasses();
    if (null != entityClasses) {
      return builderForEntityClasses(entityClasses, dbConfig);
    }
    Map<String, CollectionMetaData> collectionMetaData = new LinkedHashMap<String, CollectionMetaData>();
    Reflections reflections = new Reflections(dbConfig.getBaseScanPackage());
    Set<Class<?>> docClasses = reflections.getTypesAnnotatedWith(Document.class);
    for (Class<?> c : docClasses) {
      Document d = c.getAnnotation(Document.class);
      String collectionName = d.collection();
      String version = d.schemaVersion();
      CollectionMetaData cmd = new CollectionMetaData(collectionName, c, version, dbConfig.getSchemaComparator());
      collectionMetaData.put(collectionName, cmd);
    }
    return collectionMetaData;
  }

  private static Map<String, CollectionMetaData> builderForEntityClasses(Collection<Class<?>> entityClasses,
      JsonDBConfig dbConfig) {
    Map<String, CollectionMetaData> collectionMetaData = new LinkedHashMap<String, CollectionMetaData>();
    for (Class<?> c : entityClasses) {
      String collectionName = Util.determineCollectionName(c);
      CollectionMetaData existing = collectionMetaData.get(collectionName);
      if (null != existing) {
        if (existing.getClazz().equals(c)) {
          continue;
        }
        throw new InvalidJsonDbApiUsageException("Collection name '" + collectionName + "' is used by both "
            + existing.getClazz().getName() + " and " + c.getName());
      }
      String version = c.getAnnotation(Document.class).schemaVersion();
      collectionMetaData.put(collectionName, new CollectionMetaData(collectionName, c, version, dbConfig.getSchemaComparator()));
    }
    return collectionMetaData;
  }
}
