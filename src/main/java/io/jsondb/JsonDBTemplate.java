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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.jxpath.FunctionLibrary;
import org.apache.commons.jxpath.JXPathContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.jsondb.crypto.CryptoUtil;
import io.jsondb.crypto.ICipher;
import io.jsondb.events.CollectionFileChangeListener;
import io.jsondb.events.EventListenerList;
import io.jsondb.io.JsonDbArchive;
import io.jsondb.io.JsonFileLockException;
import io.jsondb.io.JsonReader;
import io.jsondb.io.JsonWriter;
import io.jsondb.query.Update;
import io.jsondb.query.ddl.AddOperation;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.query.ddl.DeleteOperation;
import io.jsondb.query.ddl.RenameOperation;

/**
 * @version 1.0 25-Sep-2016
 */
public class JsonDBTemplate implements JsonDBOperations {
  private Logger logger = LoggerFactory.getLogger(JsonDBTemplate.class);

  private JsonDBConfig dbConfig = null;
  private final boolean encrypted;
  private File lockFilesLocation;
  private EventListenerList eventListenerList;

  private Map<String, CollectionMetaData> cmdMap;
  private AtomicReference<Map<String, File>> fileObjectsRef = new AtomicReference<Map<String, File>>(new ConcurrentHashMap<String, File>());
  private AtomicReference<Map<String, Map<Object, ?>>> collectionsRef = new AtomicReference<Map<String, Map<Object, ?>>>(new ConcurrentHashMap<String, Map<Object, ?>>());
  private AtomicReference<Map<String, JXPathContext>> contextsRef = new AtomicReference<Map<String, JXPathContext>>(new ConcurrentHashMap<String, JXPathContext>());

  public JsonDBTemplate(String dbFilesLocationString, String baseScanPackage) {
    this(dbFilesLocationString, baseScanPackage, null, false, null);
  }

  public JsonDBTemplate(String dbFilesLocationString, String baseScanPackage, boolean compatibilityMode, Comparator<String> schemaComparator) {
    this(dbFilesLocationString, baseScanPackage, null, compatibilityMode, schemaComparator);
  }

  public JsonDBTemplate(String dbFilesLocationString, String baseScanPackage, ICipher cipher) {
    this(dbFilesLocationString, baseScanPackage, cipher, false, null);
  }

  public JsonDBTemplate(String dbFilesLocationString, String baseScanPackage, ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {
    this(new JsonDBConfig(dbFilesLocationString, baseScanPackage, cipher, compatibilityMode, schemaComparator));
  }

  /**
   * Creates a JsonDBTemplate for an explicit list of entity classes instead of scanning a package.
   * Package scanning cannot see application classes on Android or under OSGi, so use this there.
   *
   * @param dbFilesLocationString path to the folder that holds the collection .json files
   * @param entityClasses the classes annotated with {@link io.jsondb.annotation.Document} to use as collections
   */
  public JsonDBTemplate(String dbFilesLocationString, Collection<Class<?>> entityClasses) {
    this(dbFilesLocationString, entityClasses, null, false, null);
  }

  /**
   * Same as {@link #JsonDBTemplate(String, Collection)} with encryption of {@link io.jsondb.annotation.Secret} fields.
   *
   * @param dbFilesLocationString path to the folder that holds the collection .json files
   * @param entityClasses the classes annotated with {@link io.jsondb.annotation.Document} to use as collections
   * @param cipher cipher used for {@link io.jsondb.annotation.Secret} fields, or null for no encryption
   */
  public JsonDBTemplate(String dbFilesLocationString, Collection<Class<?>> entityClasses, ICipher cipher) {
    this(dbFilesLocationString, entityClasses, cipher, false, null);
  }

  /**
   * Same as {@link #JsonDBTemplate(String, Collection)} with every setting.
   *
   * @param dbFilesLocationString path to the folder that holds the collection .json files
   * @param entityClasses the classes annotated with {@link io.jsondb.annotation.Document} to use as collections
   * @param cipher cipher used for {@link io.jsondb.annotation.Secret} fields, or null for no encryption
   * @param compatibilityMode if true unknown properties in the .json files are ignored
   * @param schemaComparator comparator for schema versions, or null for the default
   */
  public JsonDBTemplate(String dbFilesLocationString, Collection<Class<?>> entityClasses, ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {
    this(new JsonDBConfig(dbFilesLocationString, entityClasses, cipher, compatibilityMode, schemaComparator));
  }

  private JsonDBTemplate(JsonDBConfig dbConfig) {
    this.dbConfig = dbConfig;
    ICipher cipher = dbConfig.getCipher();
    if (null == cipher) {
      logger.info("Encryption is not enabled for JSON DB");
      this.encrypted = false;
    } else {
      logger.info("Encryption is enabled for JSON DB");
      this.encrypted = true;
    }
    initialize();
    eventListenerList = new EventListenerList(dbConfig, cmdMap);
  }

  private void initialize(){
    this.lockFilesLocation = new File(dbConfig.getDbFilesLocation(), "lock");
    if(!lockFilesLocation.exists()) {
      lockFilesLocation.mkdirs();
    }
    if (!dbConfig.getDbFilesLocation().exists()) {
      try {
        Files.createDirectory(dbConfig.getDbFilesPath());
      } catch (IOException e) {
        logger.error("DbFiles directory does not exist. Failed to create a new empty DBFiles directory {}", e);
        throw new InvalidJsonDbApiUsageException("DbFiles directory does not exist. Failed to create a new empty DBFiles directory " + dbConfig.getDbFilesLocationString());
      }
    } else if (dbConfig.getDbFilesLocation().isFile()) {
      throw new InvalidJsonDbApiUsageException("Specified DbFiles directory is actually a file cannot use it as a directory");
    }

    cmdMap = CollectionMetaData.builder(dbConfig);

    loadDB();

    // Auto-cleanup at shutdown
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        eventListenerList.shutdown();
      }
    });
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#reLoadDB()
   */
  @Override
  public void reLoadDB() {
    loadDB();
  }

  private synchronized void loadDB() {
    for(String collectionName : cmdMap.keySet()) {
      File collectionFile = new File(dbConfig.getDbFilesLocation(), collectionName + ".json");
      if(collectionFile.exists()) {
        reloadCollection(collectionName);
      } else if (collectionsRef.get().containsKey(collectionName)){
        //this probably is a reload attempt after a collection .json was deleted.
        //that is the reason even though the file does not exist a entry into collectionsRef still exists.
        contextsRef.get().remove(collectionName);
        collectionsRef.get().remove(collectionName);
      }
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#reloadCollection(java.lang.String)
   */
  public void reloadCollection(String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    cmd.getCollectionLock().writeLock().lock();
    try {
      File collectionFile = fileObjectsRef.get().get(collectionName);
      if(null == collectionFile) {
        // Lets create a file now
        collectionFile = new File(dbConfig.getDbFilesLocation(), collectionName + ".json");
        if(!collectionFile.exists()) {
          throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' cannot be found at " + collectionFile.getAbsolutePath());
        }
        Map<String, File> fileObjectMap = fileObjectsRef.get();
        Map<String, File> newFileObjectmap = new ConcurrentHashMap<String, File>(fileObjectMap);
        newFileObjectmap.put(collectionName, collectionFile);
        fileObjectsRef.set(newFileObjectmap);
      }
      Map<Object, ?> collection = loadCollection(collectionFile, collectionName, cmd);
      if (null != collection) {
        contextsRef.get().put(collectionName, newQueryContext(collection.values()));
        collectionsRef.get().put(collectionName, collection);
      } else {
        //Since this is a reload attempt its possible the .json files have disappeared in the interim a very rare thing
        contextsRef.get().remove(collectionName);
        collectionsRef.get().remove(collectionName);
      }
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /**
   * Creates the JXPath context used to run queries against a collection.
   *
   * By default JXPath resolves extension functions to any Java static method or constructor
   * on the classpath, so a query such as {@code java.lang.Runtime.getRuntime()} would run.
   * Query strings frequently carry user input, so extension functions are disabled; the core
   * XPath functions such as contains() and count() are unaffected.
   */
  private static JXPathContext newQueryContext(Collection<?> documents) {
    JXPathContext context = JXPathContext.newContext(documents);
    context.setFunctions(new FunctionLibrary());
    return context;
  }

  private <T> Map<Object, T> loadCollection(File collectionFile, String collectionName, CollectionMetaData cmd) {
    @SuppressWarnings("unchecked")
    Class<T> entity = cmd.getClazz();
    Method getterMethodForId = cmd.getIdAnnotatedFieldGetterMethod();

    JsonReader jr = null;
    Map<Object, T> collection = new LinkedHashMap<Object, T>();

    String line = null;
    int lineNo = 1;
    try {
      jr = new JsonReader(dbConfig, collectionFile);

      while ((line = jr.readLine()) != null) {
        if (lineNo == 1) {
          SchemaVersion v = dbConfig.getObjectMapper().readValue(line, SchemaVersion.class);
          cmd.setActualSchemaVersion(v.getSchemaVersion());
        } else {
          T row = dbConfig.getObjectMapper().readValue(line, entity);
          Object id = Util.getIdForEntity(row, getterMethodForId);
          collection.put(id, row);
        }
        lineNo++;
      }
    } catch (JsonParseException je) {
      logger.error("Failed Json Parsing for file {} line {}", collectionFile.getName(), lineNo, je);
      return null;
    } catch (JsonMappingException jm) {
      logger.error("Failed Mapping Parsed Json to Entity {} for file {} line {}",
          entity.getSimpleName(), collectionFile.getName(), lineNo, jm);
      return null;
    } catch (CharacterCodingException ce) {
      logger.error("Unsupported Character Encoding in file {} expected Encoding {}",
          collectionFile.getName(), dbConfig.getCharset().displayName(), ce);
      return null;
    } catch (JsonFileLockException jfe) {
      logger.error("Failed to acquire lock for collection file {}", collectionFile.getName(), jfe);
      return null;
    } catch (FileNotFoundException fe) {
      logger.error("Collection file {} not found", collectionFile.getName(), fe);
      return null;
    } catch (IOException e) {
      logger.error("Some IO Exception reading the Json File {}", collectionFile.getName(), e);
      return null;
    } catch(Throwable t) {
      logger.error("Throwable Caught {}, {} ", collectionFile.getName(), t);
      return null;
    } finally {
      if (null != jr) {
        jr.close();
      }
    }
    return collection;
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#addCollectionFileChangeListener(org.jsondb.CollectionFileChangeListener)
   */
  @Override
  public void addCollectionFileChangeListener(CollectionFileChangeListener listener) {
    eventListenerList.addCollectionFileChangeListener(listener);
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#removeCollectionFileChangeListener(org.jsondb.CollectionFileChangeListener)
   */
  @Override
  public void removeCollectionFileChangeListener(CollectionFileChangeListener listener) {
    eventListenerList.removeCollectionFileChangeListener(listener);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#hasCollectionFileChangeListener()
   */
  @Override
  public boolean hasCollectionFileChangeListener() {
    return eventListenerList.hasCollectionFileChangeListener();
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#createCollection(java.lang.Class)
   */
  @Override
  public <T> void createCollection(Class<T> entityClass) {
    createCollection(Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#createCollection(java.lang.String)
   */
  @Override
  public <T> void createCollection(String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    if (null == cmd) {
      throw new InvalidJsonDbApiUsageException(
          "No class found with @Document Annotation and attribute collectionName as: " + collectionName);
    }
    @SuppressWarnings("unchecked")
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if (null != collection) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' already exists.");
    }

    cmd.getCollectionLock().writeLock().lock();
    try {
      // Some other thread might have created same collection when this thread reached this point
      if(collectionsRef.get().get(collectionName) != null) {
        return;
      }

      String collectionFileName = collectionName + ".json";
      File fileObject = new File(dbConfig.getDbFilesLocation(), collectionFileName);
      boolean fileCreated;
      try {
        fileCreated = fileObject.createNewFile();
      } catch (IOException e) {
        logger.error("IO Exception creating the collection file {}", collectionFileName, e);
        throw new InvalidJsonDbApiUsageException("Unable to create a collection file for collection: " + collectionName);
      }
      if (!fileCreated) {
        // The file exists but the collection is not loaded, typically because the file failed to parse.
        // Stamping a version on it would truncate it and destroy the user's data.
        throw new InvalidJsonDbApiUsageException("Collection file " + fileObject.getAbsolutePath()
            + " already exists but is not loaded, it may be corrupt. Repair or remove it, then call reloadCollection(\""
            + collectionName + "\")");
      }

      if (Util.stampVersion(dbConfig, fileObject, cmd.getSchemaVersion())) {
        collection = new LinkedHashMap<Object, T>();
        collectionsRef.get().put(collectionName, collection);
        contextsRef.get().put(collectionName, newQueryContext(collection.values()));
        fileObjectsRef.get().put(collectionName, fileObject);
        cmd.setActualSchemaVersion(cmd.getSchemaVersion());
      } else {
        fileObject.delete();
        throw new JsonDBException("Failed to stamp version for collection: " + collectionName);
      }
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#dropCollection(java.lang.Class)
   */
  @Override
  public <T> void dropCollection(Class<T> entityClass) {
    dropCollection(Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#dropCollection(java.lang.String)
   */
  @Override
  public void dropCollection(String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    if((null == cmd) || (!collectionsRef.get().containsKey(collectionName))) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().writeLock().lock();
    try {
      File toDelete = fileObjectsRef.get().get(collectionName);
      try {
        Files.deleteIfExists(toDelete.toPath());
      } catch (IOException e) {
        logger.error("IO Exception deleting the collection file {}", toDelete.getName(), e);
        throw new InvalidJsonDbApiUsageException("Unable to create a collection file for collection: " + collectionName);
      }
      //cmdMap.remove(collectionName); //Do not remove it from the CollectionMetaData Map.
      //Someone might want to re insert a new collection of this type.
      fileObjectsRef.get().remove(collectionName);
      collectionsRef.get().remove(collectionName);
      contextsRef.get().remove(collectionName);
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#updateCollectionSchema(org.jsondb.query.CollectionSchemaUpdate, java.lang.Class)
   */
  @Override
  public <T> void updateCollectionSchema(CollectionSchemaUpdate update, Class<T> entityClass) {
    updateCollectionSchema(update, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#updateCollectionSchema(org.jsondb.query.CollectionSchemaUpdate, java.lang.String)
   */
  @Override
  public <T> void updateCollectionSchema(CollectionSchemaUpdate update, String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    @SuppressWarnings("unchecked")
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    if (null == update) {
      return;
    }
    Map<String, RenameOperation> renOps = update.getRenameOperations();
    Map<String, AddOperation> addOps = update.getAddOperations();
    Map<String, DeleteOperation> delOps = update.getDeleteOperations();
    if (renOps.isEmpty() && addOps.isEmpty() && delOps.isEmpty()) {
      return;
    }

    ObjectMapper objectMapper = dbConfig.getObjectMapper();
    Set<String> classProperties = new HashSet<String>();
    for (BeanPropertyDefinition property : objectMapper.getDeserializationConfig()
        .introspect(objectMapper.constructType(cmd.getClazz())).findProperties()) {
      classProperties.add(property.getName());
    }
    // Reject an update that would silently lose data before anything is read or written
    for (Entry<String, RenameOperation> renEntry : renOps.entrySet()) {
      String newName = renEntry.getValue().getNewName();
      if (!classProperties.contains(newName)) {
        throw new InvalidJsonDbApiUsageException("Cannot rename '" + renEntry.getKey() + "' to '" + newName + "' in collection "
            + collectionName + ": " + cmd.getClazz().getSimpleName() + " has no property '" + newName + "', so the values would be lost");
      }
    }
    Map<String, JsonNode> addValues = new LinkedHashMap<String, JsonNode>();
    for (Entry<String, AddOperation> addEntry : addOps.entrySet()) {
      String fieldName = addEntry.getKey();
      AddOperation op = addEntry.getValue();
      if (!classProperties.contains(fieldName)) {
        throw new InvalidJsonDbApiUsageException("Cannot add '" + fieldName + "' to collection " + collectionName + ": "
            + cmd.getClazz().getSimpleName() + " has no property '" + fieldName + "'");
      }
      Object value = op.getDefaultValue();
      if (op.isSecret()) {
        if (!encrypted) {
          throw new InvalidJsonDbApiUsageException("Cannot add secret '" + fieldName + "' to collection " + collectionName
              + ": this JsonDBTemplate has no cipher");
        }
        if (!(value instanceof String)) {
          throw new InvalidJsonDbApiUsageException("Cannot add secret '" + fieldName + "' to collection " + collectionName
              + ": the default value of a secret must be a String");
        }
        value = dbConfig.getCipher().encrypt((String) value);
      }
      addValues.put(fieldName, objectMapper.valueToTree(value));
    }
    for (String deleteKey : delOps.keySet()) {
      if (classProperties.contains(deleteKey)) {
        throw new InvalidJsonDbApiUsageException("Cannot delete '" + deleteKey + "' from collection " + collectionName + ": "
            + cmd.getClazz().getSimpleName() + " still has that property");
      }
    }

    cmd.getCollectionLock().writeLock().lock();
    try {
      // The operations are applied to the JSON stored in the file, not to the in-memory objects. Those
      // are already instances of the new class and have lost any key it does not know, which is
      // exactly what a renamed field looks like, so renaming them lost the data (issue #57).
      ObjectReader reader = objectMapper.readerFor(cmd.getClazz()).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      File collectionFile = fileObjectsRef.get().get(collectionName);
      List<Object> documents = new ArrayList<Object>();
      Set<String> conflictingIds = new LinkedHashSet<String>();
      JsonReader jr = new JsonReader(dbConfig, collectionFile);
      try {
        String line = jr.readLine();
        int lineNo = 1;
        while ((line = jr.readLine()) != null) {
          lineNo++;
          if (line.trim().isEmpty()) {
            continue;
          }
          JsonNode node = objectMapper.readTree(line);
          if (!(node instanceof ObjectNode)) {
            throw new JsonDBException("Line " + lineNo + " of collection file " + collectionFile.getName() + " is not a JSON object");
          }
          ObjectNode document = (ObjectNode) node;
          // Take every renamed value out before setting any, so swaps and chains do not overwrite each other
          Map<String, JsonNode> renamedValues = new LinkedHashMap<String, JsonNode>();
          for (Entry<String, RenameOperation> renEntry : renOps.entrySet()) {
            JsonNode value = document.remove(renEntry.getKey());
            if (null != value) {
              renamedValues.put(renEntry.getValue().getNewName(), value);
            }
          }
          for (Entry<String, JsonNode> renamed : renamedValues.entrySet()) {
            JsonNode existing = document.get(renamed.getKey());
            if (null != existing && !existing.isNull()) {
              conflictingIds.add(document.path(cmd.getIdAnnotatedFieldName()).asText());
            }
            document.set(renamed.getKey(), renamed.getValue());
          }
          for (Entry<String, JsonNode> addEntry : addValues.entrySet()) {
            JsonNode existing = document.get(addEntry.getKey());
            if (null == existing || existing.isNull()) {
              document.set(addEntry.getKey(), addEntry.getValue());
            }
          }
          for (String deleteKey : delOps.keySet()) {
            document.remove(deleteKey);
          }
          // Mapping onto the class also drops any other key the class no longer has
          documents.add(reader.readValue(document));
        }
      } finally {
        jr.close();
      }
      if (!conflictingIds.isEmpty()) {
        throw new InvalidJsonDbApiUsageException("Cannot apply the renames to collection " + collectionName
            + ": the new property already has a value in documents with id " + conflictingIds);
      }

      JsonWriter jw = new JsonWriter(dbConfig, cmd, collectionName, collectionFile);
      if (!jw.reWriteJsonFile(documents, true)) {
        throw new JsonDBException("Failed to save the schema update for collection " + collectionName);
      }
      // Reload while still holding the lock so no other operation sees, or writes back, the old objects
      reloadCollection(collectionName);
    } catch (IOException e) {
      logger.error("Failed to update the schema of collection " + collectionName, e);
      throw new JsonDBException("Failed to update the schema of collection " + collectionName, e);
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#getCollectionNames()
   */
  @Override
  public Set<String> getCollectionNames() {
    return collectionsRef.get().keySet();
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#getCollectionName(java.lang.Class)
   */
  @Override
  public String getCollectionName(Class<?> entityClass) {
    return Util.determineCollectionName(entityClass);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#getCollection(java.lang.Class)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> List<T> getCollection(Class<T> entityClass) {
    String collectionName = Util.determineCollectionName(entityClass);
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if (null == collection) {
      createCollection(collectionName);
      collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    }

    CollectionMetaData cmd = cmdMap.get(collectionName);
    List<T> newCollection = new ArrayList<T>();
    try {
      for (T document : collection.values()) {
        Object obj = Util.deepCopy(document);
        if(encrypted && cmd.hasSecret() && null != obj) {
          CryptoUtil.decryptFields(obj, cmd, dbConfig.getCipher());
        }
        newCollection.add((T) obj);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    }
    return newCollection;
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#collectionExists(java.lang.Class)
   */
  @Override
  public <T> boolean collectionExists(Class<T> entityClass) {
    return collectionExists(Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#collectionExists(java.lang.String)
   */
  @Override
  public boolean collectionExists(String collectionName) {
    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    if(null == collectionMeta) {
      return false;
    }
    collectionMeta.getCollectionLock().readLock().lock();
    try {
      return collectionsRef.get().containsKey(collectionName);
    } finally {
      collectionMeta.getCollectionLock().readLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#isCollectionReadonly(java.lang.Class)
   */
  @Override
  public <T> boolean isCollectionReadonly(Class<T> entityClass) {
    return isCollectionReadonly(Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#isCollectionReadonly(java.lang.String)
   */
  @Override
  public <T> boolean isCollectionReadonly(String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    return cmd.isReadOnly();
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#find(java.lang.String, java.lang.Class)
   */
  @Override
  public <T> List<T> find(String jxQuery, Class<T> entityClass) {
    return find(jxQuery, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#find(java.lang.String, java.lang.String)
   */
  @Override
  public <T> List<T> find(String jxQuery, String collectionName) {
    return find(jxQuery, collectionName, null);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#find(java.lang.String, java.lang.String, java.util.Comparator)
   */
  @Override
  public <T> List<T> find(String jxQuery, Class<T> entityClass, Comparator<? super T> comparator) {
    return find(jxQuery, Util.determineCollectionName(entityClass), comparator);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#find(java.lang.String, java.lang.String, java.util.Comparator)
   */
  @Override
  public <T> List<T> find(String jxQuery, String collectionName, Comparator<? super T> comparator) {
    return find(jxQuery, collectionName, comparator, null);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#find(java.lang.String, java.lang.String, java.util.Comparator, java.lang.String)
   */
  @Override
  public <T> List<T> find(String jxQuery, Class<T> entityClass, Comparator<? super T> comparator, String slice) {
    return find(jxQuery, Util.determineCollectionName(entityClass), comparator, slice);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#find(java.lang.String, java.lang.String, java.util.Comparator, java.lang.String)
   */
  @Override
  public <T> List<T> find(String jxQuery, String collectionName, Comparator<? super T> comparator, String slice) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().readLock().lock();
    boolean isSliceable = Util.isSliceable(slice);
    try {
      JXPathContext context = contextsRef.get().get(collectionName);
      Iterator<T> resultItr = context.iterate(jxQuery);
      List<T> newCollection = new ArrayList<T>();
      while (resultItr.hasNext()) {
        T document = resultItr.next();
        if (isSliceable) {
          //Since slicing is enabled we defer the deepcopy and decryption to later stage.
          newCollection.add(document);
        } else {
          Object obj = Util.deepCopy(document);
          if (encrypted && cmd.hasSecret() && null != obj) {
            CryptoUtil.decryptFields(obj, cmd, dbConfig.getCipher());
          }
          newCollection.add((T) obj);
        }
      }
      if (comparator != null) {
        // It is tempting to attempt to sort the objects in the while loop above, but it has no real benefit
        // See: https://stackoverflow.com/questions/24136930/sort-while-inserting-or-copy-and-sort
        newCollection.sort(comparator);
      }
      if (isSliceable) {
        List<Integer> indexes = Util.getSliceIndexes(slice, newCollection.size());
        if (indexes != null) {
          List<T> slicedCollection = new ArrayList<T>(indexes.size());
          for (int index : indexes) {
            Object obj = Util.deepCopy(newCollection.get(index));
            if (encrypted && cmd.hasSecret() && null != obj) {
              CryptoUtil.decryptFields(obj, cmd, dbConfig.getCipher());
            }
            slicedCollection.add((T) obj);
          }
          return slicedCollection;
        }
      }
      return newCollection;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      cmd.getCollectionLock().readLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAll(java.lang.Class)
   */
  @Override
  public <T> List<T> findAll(Class<T> entityClass) {
    return findAll(Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAll(java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> List<T> findAll(String collectionName) {
    return findAll(collectionName, null);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAll(java.lang.Class, java.util.Comparator)
   */
  @Override
  public <T> List<T> findAll(Class<T> entityClass, Comparator<? super T> comparator) {
    return findAll(Util.determineCollectionName(entityClass), comparator);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAll(java.lang.String, java.util.Comparator)
   */
  @Override
  public <T> List<T> findAll(String collectionName, Comparator<? super T> comparator) {
    return findAll(collectionName, comparator, null);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAll(java.lang.Class, java.util.Comparator, java.lang.String)
   */
  @Override
  public <T> List<T> findAll(Class<T> entityClass, Comparator<? super T> comparator, String slice) {
    return findAll(Util.determineCollectionName(entityClass), comparator, slice);
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAll(java.lang.String, java.util.Comparator, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> List<T> findAll(String collectionName, Comparator<? super T> comparator, String slice) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().readLock().lock();
    boolean isSliceable = Util.isSliceable(slice);
    try {
      List<T> newCollection = new ArrayList<T>();
      for (T document : collection.values()) {
        if (isSliceable) {
          //Since slicing is enabled we defer the deepcopy and decryption to later stage.
          newCollection.add(document);
        } else {
          T obj = (T)Util.deepCopy(document);
          if (encrypted && cmd.hasSecret() && null != obj) {
            CryptoUtil.decryptFields(obj, cmd, dbConfig.getCipher());
          }
          newCollection.add((T) obj);
        }
      }
      if (comparator != null) {
        // It is tempting to attempt to sort the obejcts in the while loop above, but it has no real benefit
        // See: https://stackoverflow.com/questions/24136930/sort-while-inserting-or-copy-and-sort
        newCollection.sort(comparator);
      }
      if (isSliceable) {
        List<Integer> indexes = Util.getSliceIndexes(slice, newCollection.size());
        if (indexes != null) {
          List<T> slicedCollection = new ArrayList<T>(indexes.size());
          for (int index : indexes) {
            Object obj = Util.deepCopy(newCollection.get(index));
            if (encrypted && cmd.hasSecret() && null != obj) {
              CryptoUtil.decryptFields(obj, cmd, dbConfig.getCipher());
            }
            slicedCollection.add((T) obj);
          }
          return slicedCollection;
        }
      }
      return newCollection;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      cmd.getCollectionLock().readLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#count(java.lang.Class)
   */
  @Override
  public <T> int count(Class<T> entityClass) {
    return count(Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#count(java.lang.String)
   */
  @Override
  public int count(String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    Map<Object, ?> collection = collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().readLock().lock();
    try {
      return collection.size();
    } finally {
      cmd.getCollectionLock().readLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findById(java.lang.Object, java.lang.Class)
   */
  @Override
  public <T> T findById(Object id, Class<T> entityClass) {
    return findById(id, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findById(java.lang.Object, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T findById(Object id, String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || null == collection) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().readLock().lock();
    try {
      Object obj = Util.deepCopy(collection.get(id));
      if(encrypted && cmd.hasSecret() && null != obj){
        CryptoUtil.decryptFields(obj, cmd, dbConfig.getCipher());
      }
      return (T) obj;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      cmd.getCollectionLock().readLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findOne(java.lang.String, java.lang.Class)
   */
  @Override
  public <T> T findOne(String jxQuery, Class<T> entityClass) {
    return findOne(jxQuery, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findOne(java.lang.String, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T findOne(String jxQuery, String collectionName) {
    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    if((null == collectionMeta) || (!collectionsRef.get().containsKey(collectionName))) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first");
    }
    collectionMeta.getCollectionLock().readLock().lock();
    try {
      JXPathContext context = contextsRef.get().get(collectionName);
      Iterator<T> resultItr = context.iterate(jxQuery);
      while (resultItr.hasNext()) {
        T document = resultItr.next();
        Object obj = Util.deepCopy(document);
        if(encrypted && collectionMeta.hasSecret() && null!= obj){
          CryptoUtil.decryptFields(obj, collectionMeta, dbConfig.getCipher());
        }
        return (T) obj; // Return the first element we find.
      }
      return null;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      collectionMeta.getCollectionLock().readLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#insert(java.lang.Object)
   */
  @Override
  public <T> void insert(Object objectToSave) {
    if (null == objectToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object cannot be inserted into DB");
    }
    Util.ensureNotRestricted(objectToSave);
    insert(objectToSave, Util.determineEntityCollectionName(objectToSave));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#insert(java.lang.Object, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> void insert(Object objectToSave, String collectionName) {
    if (null == objectToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object cannot be inserted into DB");
    }
    Util.ensureNotRestricted(objectToSave);
    Object objToSave = Util.deepCopy(objectToSave);
    CollectionMetaData cmd = cmdMap.get(collectionName);
    cmd.getCollectionLock().writeLock().lock();
    try {
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first");
      }
      Object id = Util.getIdForEntity(objectToSave, cmd.getIdAnnotatedFieldGetterMethod());
      if(encrypted && cmd.hasSecret()){
        CryptoUtil.encryptFields(objToSave, cmd, dbConfig.getCipher());
      }
      if (null == id) {
        id = Util.setIdForEntity(objToSave, cmd.getIdAnnotatedFieldSetterMethod());
      } else if (collection.containsKey(id)) {
        throw new InvalidJsonDbApiUsageException("Object already present in Collection. Use Update or Upsert operation instead of Insert");
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }

      boolean appendResult = jw.appendToJsonFile(collection.values(), objToSave);

      if(appendResult) {
        collection.put(Util.deepCopy(id), (T) objToSave);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#insert(java.util.Collection, java.lang.Class)
   */
  @Override
  public <T> void insert(Collection<? extends T> batchToSave, Class<T> entityClass) {
    insert(batchToSave, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#insert(java.util.Collection, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> void insert(Collection<? extends T> batchToSave, String collectionName) {
    if (null == batchToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object batch cannot be inserted into DB");
    }
    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    collectionMeta.getCollectionLock().writeLock().lock();
    try {
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first");
      }
      CollectionMetaData cmd = cmdMap.get(collectionName);
      Set<Object> uniqueIds = new HashSet<Object>();
      Map<Object, T> newCollection = new LinkedHashMap<Object, T>();
      for (T o : batchToSave) {
        Object obj = Util.deepCopy(o);
        Object id = Util.getIdForEntity(obj, cmd.getIdAnnotatedFieldGetterMethod());
        if(encrypted && cmd.hasSecret()){
          CryptoUtil.encryptFields(obj, cmd, dbConfig.getCipher());
        }
        if (null == id) {
          id = Util.setIdForEntity(obj, cmd.getIdAnnotatedFieldSetterMethod());
        } else if (collection.containsKey(id)) {
          throw new InvalidJsonDbApiUsageException("Object already present in Collection. Use Update or Upsert operation instead of Insert");
        }
        if (!uniqueIds.add(id)) {
          throw new InvalidJsonDbApiUsageException("Duplicate object with id: " + id + " within the passed in parameter");
        }
        newCollection.put(Util.deepCopy(id), (T) obj);
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }
      boolean appendResult = jw.appendToJsonFile(collection.values(), newCollection.values());

      if(appendResult) {
        collection.putAll(newCollection);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      collectionMeta.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#save(java.lang.Object, java.lang.Class)
   */
  @Override
  public <T> void save(Object objectToSave, Class<T> entityClass) {
    save(objectToSave, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#save(java.lang.Object, java.lang.String)
   */
  @Override
  public <T> void save(Object objectToSave, String collectionName) {
    if (null == objectToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object cannot be updated into DB");
    }
    Util.ensureNotRestricted(objectToSave);
    Object objToSave = Util.deepCopy(objectToSave);
    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    collectionMeta.getCollectionLock().writeLock().lock();
    try {
      @SuppressWarnings("unchecked")
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
      }

      CollectionMetaData cmd = cmdMap.get(collectionName);
      Object id = Util.getIdForEntity(objToSave, cmd.getIdAnnotatedFieldGetterMethod());

      T existingObject = collection.get(id);
      if (null == existingObject) {
        throw new InvalidJsonDbApiUsageException(
            String.format("Document with Id: '%s' not found in Collection by name '%s' not found. Insert or Upsert the object first.",
                id, collectionName));
      }
      if(encrypted && cmd.hasSecret()){
        CryptoUtil.encryptFields(objToSave, cmd, dbConfig.getCipher());
      }
      JsonWriter jw = null;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }
      @SuppressWarnings("unchecked")
      boolean updateResult = jw.updateInJsonFile(collection, id, (T)objToSave);
      if (updateResult) {
        @SuppressWarnings("unchecked")
        T newObject = (T) objToSave;
        collection.put(id, newObject);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      collectionMeta.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#remove(java.lang.Object)
   */
  @Override
  public <T> T remove(Object objectToRemove) {
    return remove(objectToRemove, Util.determineEntityCollectionName(objectToRemove));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#remove(java.lang.Object, java.lang.Class)
   */
  @Override
  public <T> T remove(Object objectToRemove, Class<T> entityClass) {
    return remove(objectToRemove, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#remove(java.lang.Object, java.lang.String)
   */
  @Override
  public <T> T remove(Object objectToRemove, String collectionName) {
    if (null == objectToRemove) {
      throw new InvalidJsonDbApiUsageException("Null Object cannot be removed from DB");
    }
    Util.ensureNotRestricted(objectToRemove);

    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    collectionMeta.getCollectionLock().writeLock().lock();
    try {
      @SuppressWarnings("unchecked")
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
      }

      CollectionMetaData cmd = cmdMap.get(collectionName);
      Object id = Util.getIdForEntity(objectToRemove, cmd.getIdAnnotatedFieldGetterMethod());
      if (!collection.containsKey(id)) {
        throw new InvalidJsonDbApiUsageException(String.format("Objects with Id %s not found in collection %s", id, collectionName));
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }
      boolean substractResult = jw.removeFromJsonFile(collection, id);
      if(substractResult) {
        T objectRemoved = collection.remove(id);
        // Don't need to clone it, this object no more exists in the collection
        return objectRemoved;
      } else {
        return null;
      }
    } finally {
      collectionMeta.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#remove(java.util.Collection, java.lang.Class)
   */
  @Override
  public <T> List<T> remove(Collection<? extends T> batchToRemove, Class<T> entityClass) {
    return remove(batchToRemove, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#remove(java.util.Collection, java.lang.String)
   */
  @Override
  public <T> List<T> remove(Collection<? extends T> batchToRemove, String collectionName) {
    if (null == batchToRemove) {
      throw new InvalidJsonDbApiUsageException("Null Object batch cannot be removed from DB");
    }
    CollectionMetaData cmd = cmdMap.get(collectionName);
    cmd.getCollectionLock().writeLock().lock();
    try {
      @SuppressWarnings("unchecked")
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
      }

      Set<Object> removeIds = new HashSet<Object>();

      for (T o : batchToRemove) {
        Object id = Util.getIdForEntity(o, cmd.getIdAnnotatedFieldGetterMethod());
        if (collection.containsKey(id)) {
          removeIds.add(id);
        }
      }

      if(removeIds.size() < 1) {
        return null;
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }
      boolean substractResult = jw.removeFromJsonFile(collection, removeIds);

      List<T> removedObjects = null;
      if(substractResult) {
        removedObjects = new ArrayList<T>();
        for (Object id : removeIds) {
          // Don't need to clone it, this object no more exists in the collection
          removedObjects.add(collection.remove(id));
        }
      }
      return removedObjects;
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#upsert(java.lang.Object)
   */
  @Override
  public <T> void upsert(Object objectToSave) {
    if (null == objectToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object cannot be upserted into DB");
    }
    Util.ensureNotRestricted(objectToSave);
    upsert(objectToSave, Util.determineEntityCollectionName(objectToSave));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#upsert(java.lang.Object, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> void upsert(Object objectToSave, String collectionName) {
    if (null == objectToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object cannot be upserted into DB");
    }
    Util.ensureNotRestricted(objectToSave);
    Object objToSave = Util.deepCopy(objectToSave);
    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    collectionMeta.getCollectionLock().writeLock().lock();
    try {
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first");
      }
      CollectionMetaData cmd = cmdMap.get(collectionName);
      Object id = Util.getIdForEntity(objectToSave, cmd.getIdAnnotatedFieldGetterMethod());
      if(encrypted && cmd.hasSecret()){
        CryptoUtil.encryptFields(objToSave, cmd, dbConfig.getCipher());
      }

      boolean insert = true;
      if (null == id) {
        id = Util.setIdForEntity(objToSave, cmd.getIdAnnotatedFieldSetterMethod());
      } else if (collection.containsKey(id)) {
        insert = false;
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }

      if (insert) {
        boolean insertResult = jw.appendToJsonFile(collection.values(), objToSave);
        if(insertResult) {
          collection.put(Util.deepCopy(id), (T) objToSave);
        }
      } else {
        boolean updateResult = jw.updateInJsonFile(collection, id, (T)objToSave);
        if (updateResult) {
          T newObject = (T) objToSave;
          collection.put(id, newObject);
        }
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      collectionMeta.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#upsert(java.util.Collection, java.lang.Class)
   */
  @Override
  public <T> void upsert(Collection<? extends T> batchToSave, Class<T> entityClass) {
    upsert(batchToSave, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#upsert(java.util.Collection, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> void upsert(Collection<? extends T> batchToSave, String collectionName) {
    if (null == batchToSave) {
      throw new InvalidJsonDbApiUsageException("Null Object batch cannot be upserted into DB");
    }
    CollectionMetaData collectionMeta = cmdMap.get(collectionName);
    collectionMeta.getCollectionLock().writeLock().lock();
    try {
      Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
      if (null == collection) {
        throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first");
      }
      CollectionMetaData cmd = cmdMap.get(collectionName);
      Set<Object> uniqueIds = new HashSet<Object>();

      Map<Object, T> collectionToInsert = new LinkedHashMap<Object, T>();
      Map<Object, T> collectionToUpdate = new LinkedHashMap<Object, T>();

      for (T o : batchToSave) {
        Object obj = Util.deepCopy(o);
        Object id = Util.getIdForEntity(obj, cmd.getIdAnnotatedFieldGetterMethod());
        if(encrypted && cmd.hasSecret()){
          CryptoUtil.encryptFields(obj, cmd, dbConfig.getCipher());
        }
        boolean insert = true;
        if (null == id) {
          id = Util.setIdForEntity(obj, cmd.getIdAnnotatedFieldSetterMethod());
        } else if (collection.containsKey(id)) {
          insert = false;
        }
        if (!uniqueIds.add(id)) {
          throw new InvalidJsonDbApiUsageException("Duplicate object with id: " + id + " within the passed in parameter");
        }
        if (insert) {
          collectionToInsert.put(Util.deepCopy(id), (T) obj);
        } else {
          collectionToUpdate.put(Util.deepCopy(id), (T) obj);
        }
      }

      if (collectionToInsert.isEmpty() && collectionToUpdate.isEmpty()) {
        return;
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }

      // Write inserts and updates in a single rewrite: a JsonWriter closes its channel after one
      // operation, so a second write through it fails (issue #62), and two writes are not atomic.
      // Updated documents keep their position and new documents are appended, as before.
      Map<Object, T> upsertedCollection = new LinkedHashMap<Object, T>(collection);
      upsertedCollection.putAll(collectionToUpdate);
      upsertedCollection.putAll(collectionToInsert);
      boolean writeResult = jw.reWriteJsonFile(upsertedCollection.values(), false);
      if (writeResult) {
        collection.putAll(collectionToUpdate);
        collection.putAll(collectionToInsert);
      }
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      collectionMeta.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#findAndRemove(java.lang.String, java.lang.Class)
   */
  @Override
  public <T> T findAndRemove(String jxQuery, Class<T> entityClass) {
    return findAndRemove(jxQuery, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#findAndRemove(java.lang.String, java.lang.String)
   */
  @Override
  public <T> T findAndRemove(String jxQuery, String collectionName) {
    if (null == jxQuery) {
      throw new InvalidJsonDbApiUsageException("Query string cannot be null.");
    }
    CollectionMetaData cmd = cmdMap.get(collectionName);
    @SuppressWarnings("unchecked")
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().writeLock().lock();
    try {
      JXPathContext context = contextsRef.get().get(collectionName);
      @SuppressWarnings("unchecked")
      Iterator<T> resultItr = context.iterate(jxQuery);
      T objectToRemove = null;
      while (resultItr.hasNext()) {
        objectToRemove = resultItr.next();
        break; // Use only the first element we find.
      }
      if (null != objectToRemove) {
        Object idToRemove = Util.getIdForEntity(objectToRemove, cmd.getIdAnnotatedFieldGetterMethod());
        if (!collection.containsKey(idToRemove)) { //This will never happen since the object was located based of jxQuery
          throw new InvalidJsonDbApiUsageException(String.format("Objects with Id %s not found in collection %s", idToRemove, collectionName));
        }

        JsonWriter jw;
        try {
          jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
        } catch (IOException ioe) {
          logger.error("Failed to obtain writer for " + collectionName, ioe);
          throw new JsonDBException("Failed to save " + collectionName, ioe);
        }
        boolean substractResult = jw.removeFromJsonFile(collection, idToRemove);
        if (substractResult) {
          T objectRemoved = collection.remove(idToRemove);
          // Don't need to clone it, this object no more exists in the collection
          return objectRemoved;
        } else {
          logger.error("Unexpected, Failed to substract the object");
        }
      }
      return null; //Either the jxQuery found nothing or actual FileIO failed to substract it.
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#findAllAndRemove(java.lang.String, java.lang.Class)
   */
  @Override
  public <T> List<T> findAllAndRemove(String jxQuery, Class<T> entityClass) {
    return findAllAndRemove(jxQuery, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#findAllAndRemove(java.lang.String, java.lang.String)
   */
  @Override
  public <T> List<T> findAllAndRemove(String jxQuery, String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    @SuppressWarnings("unchecked")
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().writeLock().lock();
    try {
      JXPathContext context = contextsRef.get().get(collectionName);
      @SuppressWarnings("unchecked")
      Iterator<T> resultItr = context.iterate(jxQuery);
      Set<Object> removeIds = new HashSet<Object>();
      while (resultItr.hasNext()) {
        T objectToRemove = resultItr.next();
        Object idToRemove = Util.getIdForEntity(objectToRemove, cmd.getIdAnnotatedFieldGetterMethod());
        removeIds.add(idToRemove);
      }

      if(removeIds.size() < 1) {
        return null;
      }

      JsonWriter jw;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }
      boolean substractResult = jw.removeFromJsonFile(collection, removeIds);

      List<T> removedObjects = null;
      if(substractResult) {
        removedObjects = new ArrayList<T>();
        for (Object id : removeIds) {
          // Don't need to clone it, this object no more exists in the collection
          removedObjects.add(collection.remove(id));
        }
      }
      return removedObjects;

    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#findAndModify(java.lang.String, org.jsondb.query.Update, java.lang.Class)
   */
  @Override
  public <T> T findAndModify(String jxQuery, Update update, Class<T> entityClass) {
    return findAndModify(jxQuery, update, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#findAndModify(java.lang.String, org.jsondb.query.Update, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T findAndModify(String jxQuery, Update update, String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().writeLock().lock();
    try {
      JXPathContext context = contextsRef.get().get(collectionName);
      Iterator<T> resultItr = context.iterate(jxQuery);
      T objectToModify = null;
      T clonedModifiedObject = null;

      while (resultItr.hasNext()) {
        objectToModify = resultItr.next();
        break; // Use only the first element we find.
      }
      if (null != objectToModify) {
        //Work on a modified copy because we dont want to touch the in-memory object until we have really saved it
        clonedModifiedObject = applyUpdate(objectToModify, update, cmd, collectionName);

        Object idToModify = Util.getIdForEntity(clonedModifiedObject, cmd.getIdAnnotatedFieldGetterMethod());
        JsonWriter jw = null;
        try {
          jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
        } catch (IOException ioe) {
          logger.error("Failed to obtain writer for " + collectionName, ioe);
          throw new JsonDBException("Failed to save " + collectionName, ioe);
        }
        boolean updateResult = jw.updateInJsonFile(collection, idToModify, clonedModifiedObject);
        if (updateResult) {
         collection.put(idToModify, clonedModifiedObject);
         //Clone it once more because we want to disconnect it from the in-memory objects before returning.
         T returnObj = (T) Util.deepCopy(clonedModifiedObject);
         if(encrypted && cmd.hasSecret() && null!= returnObj){
           CryptoUtil.decryptFields(returnObj, cmd, dbConfig.getCipher());
         }
         return returnObj;
        }
      }
      return null;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }


  /**
   * Returns a copy of the document with the update applied, leaving the document itself untouched.
   *
   * Values are converted with the same Jackson rules used to read the collection files, so an update
   * accepts what the file format accepts. A key can name a nested property with dots, such as
   * "address.city". An update that names an unknown property, sets null into a primitive or would
   * lose data (such as 7.9 into a long) is rejected.
   */
  @SuppressWarnings("unchecked")
  private <T> T applyUpdate(T document, Update update, CollectionMetaData cmd, String collectionName) {
    ObjectMapper objectMapper = dbConfig.getObjectMapper();
    ObjectNode root = objectMapper.valueToTree(document);
    for (Entry<String, Object> entry : update.getUpdateData().entrySet()) {
      String key = entry.getKey();
      Object newValue = entry.getValue();
      if (encrypted && cmd.hasSecret() && cmd.isSecretField(key) && null != newValue) {
        newValue = dbConfig.getCipher().encrypt(newValue.toString());
      }
      String[] path = key.split("\\.");
      ObjectNode parent = root;
      for (int i = 0; i < path.length - 1; i++) {
        JsonNode child = parent.get(path[i]);
        if (!(child instanceof ObjectNode)) {
          throw new InvalidJsonDbApiUsageException("Cannot update '" + key + "' in collection " + collectionName
              + ": '" + path[i] + "' is not an object");
        }
        parent = (ObjectNode) child;
      }
      parent.set(path[path.length - 1], objectMapper.valueToTree(newValue));
    }
    try {
      return (T) objectMapper.readerFor(document.getClass())
          .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
          .readValue(root);
    } catch (JsonMappingException e) {
      StringBuilder property = new StringBuilder();
      for (JsonMappingException.Reference ref : e.getPath()) {
        if (null != ref.getFieldName()) {
          property.append(property.length() > 0 ? "." : "").append(ref.getFieldName());
        }
      }
      throw new InvalidJsonDbApiUsageException("Cannot apply update to '" + property + "' in collection "
          + collectionName + ": " + e.getOriginalMessage(), e);
    } catch (IOException e) {
      throw new InvalidJsonDbApiUsageException("Cannot apply update in collection " + collectionName, e);
    }
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAllAndModify(java.lang.String, io.jsondb.query.Update, java.lang.Class)
   */
  @Override
  public <T> List<T> findAllAndModify(String jxQuery, Update update, Class<T> entityClass) {
    return findAllAndModify(jxQuery, update, Util.determineCollectionName(entityClass));
  }

  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#findAllAndModify(java.lang.String, io.jsondb.query.Update, java.lang.String)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> List<T> findAllAndModify(String jxQuery, Update update, String collectionName) {
    CollectionMetaData cmd = cmdMap.get(collectionName);
    Map<Object, T> collection = (Map<Object, T>) collectionsRef.get().get(collectionName);
    if((null == cmd) || (null == collection)) {
      throw new InvalidJsonDbApiUsageException("Collection by name '" + collectionName + "' not found. Create collection first.");
    }
    cmd.getCollectionLock().writeLock().lock();
    try {
      JXPathContext context = contextsRef.get().get(collectionName);
      Iterator<T> resultItr = context.iterate(jxQuery);
      Map<Object, T> clonedModifiedObjects = new HashMap<Object, T>();

      while (resultItr.hasNext()) {
        T objectToModify = resultItr.next();
        T clonedModifiedObject = applyUpdate(objectToModify, update, cmd, collectionName);
        Object id = Util.getIdForEntity(clonedModifiedObject, cmd.getIdAnnotatedFieldGetterMethod());
        clonedModifiedObjects.put(id, clonedModifiedObject);
      }

      JsonWriter jw = null;
      try {
        jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
      } catch (IOException ioe) {
        logger.error("Failed to obtain writer for " + collectionName, ioe);
        throw new JsonDBException("Failed to save " + collectionName, ioe);
      }
      boolean updateResult = jw.updateInJsonFile(collection, clonedModifiedObjects);
      if (updateResult) {
       collection.putAll(clonedModifiedObjects);
       //Clone it once more because we want to disconnect it from the in-memory objects before returning.
       List<T> returnObjects = new ArrayList<T>();
       for (T obj : clonedModifiedObjects.values()) {
         //Clone it once more because we want to disconnect it from the in-memory objects before returning.
         T returnObj = (T) Util.deepCopy(obj);
         if(encrypted && cmd.hasSecret() && null!= returnObj){
           CryptoUtil.decryptFields(returnObj, cmd, dbConfig.getCipher());
         }
         returnObjects.add(returnObj);
       }
       return returnObjects;
      }
      return null;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when decrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      cmd.getCollectionLock().writeLock().unlock();
    }
  }



  /* (non-Javadoc)
   * @see io.jsondb.JsonDBOperations#changeEncryption(io.jsondb.crypto.ICipher)
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> void changeEncryption(ICipher newCipher) {
    if (!encrypted) {
      throw new InvalidJsonDbApiUsageException("DB is not encrypted, nothing to change for EncryptionKey");
    }

    for (Entry<String, Map<Object, ?>> entry : collectionsRef.get().entrySet()) {
      CollectionMetaData cmd = cmdMap.get(entry.getKey());
      if (cmd.hasSecret()) {
        cmd.getCollectionLock().writeLock().lock();
      }
    }
    String collectionName = null;
    try {
      for (Entry<String, Map<Object, ?>> entry : collectionsRef.get().entrySet()) {
        collectionName = entry.getKey();
        Map<Object, T> collection = (Map<Object, T>) entry.getValue();

        CollectionMetaData cmd = cmdMap.get(collectionName);
        if (cmd.hasSecret()) {
          Map<Object, T> reCryptedObjects = new LinkedHashMap<Object, T>();
          for (Entry<Object, T> object : collection.entrySet()) {
            T clonedObject = (T) Util.deepCopy(object.getValue());
            CryptoUtil.decryptFields(clonedObject, cmd, dbConfig.getCipher());
            CryptoUtil.encryptFields(clonedObject, cmd, newCipher);
            //We will reuse the Id in the previous collection, should hopefully not cause any issues
            reCryptedObjects.put(object.getKey(), clonedObject);
          }
          JsonWriter jw = null;
          try {
            jw = new JsonWriter(dbConfig, cmd, collectionName, fileObjectsRef.get().get(collectionName));
          } catch (IOException ioe) {
            logger.error("Failed to obtain writer for " + collectionName, ioe);
            throw new JsonDBException("Failed to save " + collectionName, ioe);
          }
          boolean updateResult = jw.updateInJsonFile(collection, reCryptedObjects);
          if (!updateResult) {
            throw new JsonDBException("Failed to write re-crypted collection data to .json files, database might have become insconsistent");
          }
          collection.putAll(reCryptedObjects);
        }
      }
      dbConfig.setCipher(newCipher);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      logger.error("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
      throw new JsonDBException("Error when encrypting value for a @Secret annotated field for entity: " + collectionName, e);
    } finally {
      for (Entry<String, Map<Object, ?>> entry : collectionsRef.get().entrySet()) {
        CollectionMetaData cmd = cmdMap.get(entry.getKey());
        if (cmd.hasSecret()) {
          cmd.getCollectionLock().writeLock().unlock();
        }
      }
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#backup(java.lang.String)
   */
  @Override
  public void backup(String backupPath) {
    File zipFile;
    try {
      zipFile = JsonDbArchive.resolveBackupZipFile(backupPath);
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonDbApiUsageException(e.getMessage());
    }
    File dbDirectory = dbConfig.getDbFilesLocation();
    lockAllCollectionsWrite();
    try {
      JsonDbArchive.createBackupZip(dbDirectory, zipFile);
      logger.info("Created JsonDB backup at {}", zipFile.getAbsolutePath());
    } catch (IOException e) {
      logger.error("Failed to create JsonDB backup at {}", zipFile.getAbsolutePath(), e);
      throw new JsonDBException("Failed to create JsonDB backup at " + zipFile.getAbsolutePath(), e);
    } finally {
      unlockAllCollectionsWrite();
    }
  }

  /* (non-Javadoc)
   * @see org.jsondb.JsonDBOperations#restore(java.lang.String, boolean)
   */
  @Override
  public void restore(String restorePath, boolean merge) {
    File zipFile;
    try {
      zipFile = JsonDbArchive.resolveRestoreZipFile(restorePath);
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonDbApiUsageException(e.getMessage());
    }
    if (!zipFile.isFile()) {
      throw new InvalidJsonDbApiUsageException("Restore backup file not found: " + zipFile.getAbsolutePath());
    }
    if (merge) {
      restoreMerge(zipFile);
    } else {
      restoreReplace(zipFile);
    }
  }

  private void restoreReplace(File zipFile) {
    File dbDirectory = dbConfig.getDbFilesLocation();
    lockAllCollectionsWrite();
    try {
      JsonDbArchive.extractReplaceCollectionFiles(dbDirectory, zipFile);
      reLoadDB();
      logger.info("Restored JsonDB from backup {} (replace mode)", zipFile.getAbsolutePath());
    } catch (IOException e) {
      logger.error("Failed to restore JsonDB from backup {}", zipFile.getAbsolutePath(), e);
      throw new JsonDBException("Failed to restore JsonDB from backup " + zipFile.getAbsolutePath(), e);
    } finally {
      unlockAllCollectionsWrite();
    }
  }

  private void restoreMerge(File zipFile) {
    File tempDirectory = null;
    try {
      tempDirectory = Files.createTempDirectory("jsondb-restore-").toFile();
      Map<String, File> extractedCollections = JsonDbArchive.extractToDirectory(tempDirectory, zipFile);
      File dbDirectory = dbConfig.getDbFilesLocation();

      for (Entry<String, File> entry : extractedCollections.entrySet()) {
        String collectionName = entry.getKey();
        File backupCollectionFile = entry.getValue();
        CollectionMetaData cmd = cmdMap.get(collectionName);

        if (null == cmd) {
          File targetFile = new File(dbDirectory, backupCollectionFile.getName());
          JsonDbArchive.copyCollectionFile(backupCollectionFile, targetFile);
          continue;
        }

        if (collectionExists(collectionName)) {
          mergeCollectionFromBackup(collectionName, cmd, backupCollectionFile);
        } else {
          File targetFile = new File(dbDirectory, backupCollectionFile.getName());
          JsonDbArchive.copyCollectionFile(backupCollectionFile, targetFile);
          reloadCollection(collectionName);
        }
      }
      logger.info("Restored JsonDB from backup {} (merge mode)", zipFile.getAbsolutePath());
    } catch (IOException e) {
      logger.error("Failed to merge JsonDB from backup {}", zipFile.getAbsolutePath(), e);
      throw new JsonDBException("Failed to merge JsonDB from backup " + zipFile.getAbsolutePath(), e);
    } finally {
      if (null != tempDirectory) {
        Util.delete(tempDirectory);
      }
    }
  }

  private void mergeCollectionFromBackup(String collectionName, CollectionMetaData cmd, File backupCollectionFile) {
    List<Object> documents = readEntityDocumentsFromFile(backupCollectionFile, cmd);
    if (encrypted && cmd.hasSecret()) {
      // Backup files hold @Secret fields already encrypted and upsert encrypts them again, so decrypt first
      for (Object document : documents) {
        try {
          CryptoUtil.decryptFields(document, cmd, dbConfig.getCipher());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
          throw new JsonDBException("Failed to decrypt a @Secret field in the backup of collection " + collectionName, e);
        }
      }
    }
    // One batch upsert rewrites the collection file once instead of once per document
    upsert(documents, collectionName);
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> readEntityDocumentsFromFile(File collectionFile, CollectionMetaData cmd) {
    Class<T> entity = cmd.getClazz();
    List<T> documents = new ArrayList<T>();
    JsonReader jr = null;
    try {
      jr = new JsonReader(dbConfig, collectionFile);
      String line = null;
      int lineNo = 1;
      while ((line = jr.readLine()) != null) {
        if (lineNo++ > 1) {
          documents.add(dbConfig.getObjectMapper().readValue(line, entity));
        }
      }
    } catch (IOException e) {
      logger.error("Failed to read documents from backup file {}", collectionFile.getName(), e);
      throw new JsonDBException("Failed to read documents from backup file " + collectionFile.getName(), e);
    } finally {
      if (null != jr) {
        jr.close();
      }
    }
    return documents;
  }

  private void lockAllCollectionsWrite() {
    List<String> collectionNames = new ArrayList<String>(cmdMap.keySet());
    Collections.sort(collectionNames);
    for (String collectionName : collectionNames) {
      cmdMap.get(collectionName).getCollectionLock().writeLock().lock();
    }
  }

  private void unlockAllCollectionsWrite() {
    List<String> collectionNames = new ArrayList<String>(cmdMap.keySet());
    Collections.sort(collectionNames);
    for (int i = collectionNames.size() - 1; i >= 0; i--) {
      cmdMap.get(collectionNames.get(i)).getCollectionLock().writeLock().unlock();
    }
  }
}
