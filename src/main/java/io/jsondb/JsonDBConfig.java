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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.jsondb.crypto.ICipher;

/**
 * A POJO that has settings for the functioning of DB.
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class JsonDBConfig {
  //Settings
  private Charset charset;
  private String dbFilesLocationString;
  private Path dbFilesPath;
  private String baseScanPackage;
  private Collection<Class<?>> entityClasses;
  private ICipher cipher;
  private boolean compatibilityMode;
  private volatile boolean syncFolderOnWrite;
  private boolean readOnly;
  private volatile IdGenerator defaultIdGenerator;
  private final Map<Class<?>, IdGenerator> idGenerators = new ConcurrentHashMap<Class<?>, IdGenerator>();

  //References
  private ObjectMapper objectMapper;
  private Comparator<String> schemaComparator;

  public JsonDBConfig(String dbFilesLocationString, String baseScanPackage,
      ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {
    this(new File(dbFilesLocationString).toPath(), baseScanPackage, cipher, compatibilityMode, schemaComparator);
    this.dbFilesLocationString = dbFilesLocationString;
  }

  /**
   * Same as {@link #JsonDBConfig(String, String, ICipher, boolean, Comparator)} for a folder given as a
   * Path, which can be on any file system, not only the default one
   *
   * @param dbFilesPath the folder that holds the collection .json files
   * @param baseScanPackage the package to scan for classes annotated with {@link io.jsondb.annotation.Document}
   * @param cipher cipher used for {@link io.jsondb.annotation.Secret} fields, or null for no encryption
   * @param compatibilityMode if true unknown properties in the .json files are ignored
   * @param schemaComparator comparator for schema versions, or null for the default
   */
  public JsonDBConfig(Path dbFilesPath, String baseScanPackage,
      ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {

    this.charset = Charset.forName("UTF-8");
    this.dbFilesLocationString = dbFilesPath.toString();
    this.dbFilesPath = dbFilesPath;
    this.baseScanPackage = baseScanPackage;
    this.cipher = cipher;

    this.compatibilityMode = compatibilityMode;
    this.objectMapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule());

    if (compatibilityMode) {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    if (null == schemaComparator) {
      this.schemaComparator = new DefaultSchemaVersionComparator();
    } else {
      this.schemaComparator = schemaComparator;
    }
  }

  /**
   * Creates a config with an explicit list of entity classes instead of a package to scan.
   * Package scanning cannot see application classes on Android or under OSGi, so this is the way
   * to use JsonDB there.
   *
   * @param dbFilesLocationString path to the folder that holds the collection .json files
   * @param entityClasses the classes annotated with {@link io.jsondb.annotation.Document} to use as collections
   * @param cipher cipher used for {@link io.jsondb.annotation.Secret} fields, or null for no encryption
   * @param compatibilityMode if true unknown properties in the .json files are ignored
   * @param schemaComparator comparator for schema versions, or null for the default
   */
  public JsonDBConfig(String dbFilesLocationString, Collection<Class<?>> entityClasses,
      ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {
    this(new File(dbFilesLocationString).toPath(), entityClasses, cipher, compatibilityMode, schemaComparator);
    this.dbFilesLocationString = dbFilesLocationString;
  }

  /**
   * Same as {@link #JsonDBConfig(String, Collection, ICipher, boolean, Comparator)} for a folder given as a
   * Path, which can be on any file system, not only the default one
   *
   * @param dbFilesPath the folder that holds the collection .json files
   * @param entityClasses the classes annotated with {@link io.jsondb.annotation.Document} to use as collections
   * @param cipher cipher used for {@link io.jsondb.annotation.Secret} fields, or null for no encryption
   * @param compatibilityMode if true unknown properties in the .json files are ignored
   * @param schemaComparator comparator for schema versions, or null for the default
   */
  public JsonDBConfig(Path dbFilesPath, Collection<Class<?>> entityClasses,
      ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {
    this(dbFilesPath, (String) null, cipher, compatibilityMode, schemaComparator);
    if (null == entityClasses) {
      throw new InvalidJsonDbApiUsageException("Entity classes cannot be null");
    }
    this.entityClasses = Collections.unmodifiableList(new ArrayList<Class<?>>(entityClasses));
  }

  public Charset getCharset() {
    return charset;
  }
  public void setCharset(Charset charset) {
    this.charset = charset;
  }
  public String getDbFilesLocationString() {
    return dbFilesLocationString;
  }
  public void setDbFilesLocationString(String dbFilesLocationString) {
    this.dbFilesLocationString = dbFilesLocationString;
    this.dbFilesPath = new File(dbFilesLocationString).toPath();
  }
  /**
   * @return the database folder as a File
   * @throws UnsupportedOperationException if the folder is not on the default file system, use
   *     {@link #getDbFilesPath()} instead
   */
  public File getDbFilesLocation() {
    return dbFilesPath.toFile();
  }
  public Path getDbFilesPath() {
    return dbFilesPath;
  }

  public String getBaseScanPackage() {
    return baseScanPackage;
  }
  public void setBaseScanPackage(String baseScanPackage) {
    this.baseScanPackage = baseScanPackage;
  }
  /**
   * @return the explicitly registered entity classes, or null when classes are found by scanning
   *         {@link #getBaseScanPackage()}
   */
  public Collection<Class<?>> getEntityClasses() {
    return entityClasses;
  }
  public ICipher getCipher() {
    return cipher;
  }
  public void setCipher(ICipher cipher) {
    this.cipher = cipher;
  }
  public boolean isCompatibilityMode() {
    return compatibilityMode;
  }
  public void setCompatibilityMode(boolean compatibilityMode) {
    this.compatibilityMode = compatibilityMode;
    if (compatibilityMode) {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    } else {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }
  }
  public boolean isSyncFolderOnWrite() {
    return syncFolderOnWrite;
  }
  public void setSyncFolderOnWrite(boolean syncFolderOnWrite) {
    this.syncFolderOnWrite = syncFolderOnWrite;
  }
  public boolean isReadOnly() {
    return readOnly;
  }
  /**
   * Opens the database read-only: collections load without lock files, so the folder does not have
   * to be writable, and every operation that would change the database throws
   * {@link io.jsondb.InvalidJsonDbApiUsageException}. Set it before passing this config to
   * {@link io.jsondb.JsonDBTemplate#JsonDBTemplate(JsonDBConfig)}; a template reads it once, when it is created.
   *
   * @param readOnly true to open the database read-only
   */
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  /**
   * Sets the IdGenerator for every collection that has none of its own.
   *
   * @param idGenerator the generator, or null for JsonDB's built-in ids
   */
  public void setIdGenerator(IdGenerator idGenerator) {
    this.defaultIdGenerator = idGenerator;
  }

  /**
   * Sets the IdGenerator for one collection's class, in place of the default one.
   *
   * @param entityClass the {@link io.jsondb.annotation.Document} class
   * @param idGenerator the generator, or null to use the default one again
   */
  public void setIdGenerator(Class<?> entityClass, IdGenerator idGenerator) {
    if (null == idGenerator) {
      idGenerators.remove(entityClass);
    } else {
      idGenerators.put(entityClass, idGenerator);
    }
  }

  /**
   * @param entityClass the {@link io.jsondb.annotation.Document} class
   * @return the class's IdGenerator, else the default one, else null for JsonDB's built-in ids
   */
  public IdGenerator getIdGenerator(Class<?> entityClass) {
    IdGenerator idGenerator = idGenerators.get(entityClass);
    return null != idGenerator ? idGenerator : defaultIdGenerator;
  }
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }
  public Comparator<String> getSchemaComparator() {
    return schemaComparator;
  }
}
