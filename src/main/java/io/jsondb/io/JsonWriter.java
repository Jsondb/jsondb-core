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
package io.jsondb.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsondb.CollectionMetaData;
import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBException;
import io.jsondb.SchemaVersion;

/**
 * A special File Writer to write to the .json DB files that ensures
 * proper character encoding is used and the necessary File Locks are created.
 *
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class JsonWriter {

  private Logger logger = LoggerFactory.getLogger(JsonWriter.class);

  private File dbFilesLocation;
  private String collectionName;
  private File collectionFile;
  private Charset charset;
  private ObjectMapper objectMapper;
  private SchemaVersion schemaVersion;
  private CollectionMetaData cmd;
  private boolean syncFolderOnWrite;

  private File lockFilesLocation;
  private File fileLockLocation;
  
  private RandomAccessFile raf;
  private FileChannel channel;

  public JsonWriter(JsonDBConfig dbConfig, CollectionMetaData cmd, String collectionName, File collectionFile) throws IOException {

    this.dbFilesLocation = dbConfig.getDbFilesLocation();
    this.collectionName = collectionName;
    this.collectionFile = collectionFile;
    this.charset = dbConfig.getCharset();
    this.objectMapper = dbConfig.getObjectMapper();
    this.schemaVersion = new SchemaVersion(cmd.getSchemaVersion());
    this.cmd = cmd;
    this.syncFolderOnWrite = dbConfig.isSyncFolderOnWrite();
    
    this.lockFilesLocation = new File(collectionFile.getParentFile(), "lock");
    this.fileLockLocation = new File(lockFilesLocation, collectionFile.getName() + ".lock");
    
    if(!lockFilesLocation.exists()) {
      lockFilesLocation.mkdirs();
    }
    if(!fileLockLocation.exists()) {
      fileLockLocation.createNewFile();
    }
    
    raf = new RandomAccessFile(fileLockLocation, "rw");
    channel = raf.getChannel();
  }
  
  private FileLock acquireLock() throws IOException {
    try {
      FileLock fileLock= channel.lock();
      return fileLock;
    } catch (IOException e) {
      try {
        channel.close();
        raf.close();
      } catch (IOException e1) {
        logger.error("Failed while closing RandomAccessFile for collection file {}", collectionFile.getName());
      }
      throw e;
    }
  }
  
  private void releaseLock(FileLock lock) {
    try {
      if(lock != null && lock.isValid()) {
        lock.release();
      }
    } catch (IOException e) {
      logger.error("Failed to release lock for collection file {}", collectionFile.getName(), e);
    }
    try {
      channel.close();
    } catch (IOException e) {
      logger.error("Failed to close FileChannel for collection file {}", collectionFile.getName(), e);
    }
    try {
      raf.close();
    } catch (IOException e) {
      logger.error("Failed to close RandomAccessFile for collection file {}", collectionFile.getName(), e);
    }
  }

  /**
   * Atomically replaces the collection file with the fully written temporary file.
   *
   * If the move fails the collection file is left as it was, so the caller must not apply the
   * change in memory either. The temporary file is deleted and a JsonDBException is thrown
   * rather than reporting success (issue #15).
   */
  private void moveIntoPlace(File tFile) {
    try {
      Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      if (syncFolderOnWrite) {
        syncDirectory(collectionFile.getParentFile());
      }
    } catch (IOException e) {
      logger.error("Failed to move temporary collection file {} to collection file {}", tFile.getName(), collectionFile.getName(), e);
      try {
        Files.deleteIfExists(tFile.toPath());
      } catch (IOException de) {
        logger.error("Failed to delete temporary collection file {}", tFile.getName(), de);
      }
      throw new JsonDBException("Failed to save collection " + collectionName
          + ", could not replace collection file " + collectionFile.getAbsolutePath(), e);
    }
  }

  /**
   * The one write path behind every public method: writes the schema version line and then the
   * documents, one per line, to a temporary file in the database folder, and atomically moves it over
   * the collection file, all while holding the collection's file lock.
   *
   * @param documents the documents the collection file must contain, in order
   * @param ignoreReadonly write even if the collection is loaded readonly; schema updates need this
   * @param lineTransform applied to each serialized document before it is written, or null
   * @return true if the file was replaced, false if the lock or the temporary file could not be had or
   *         the temporary file could not be written; the collection file is unchanged then
   */
  private boolean writeCollectionFile(Collection<?> documents, boolean ignoreReadonly, UnaryOperator<String> lineTransform) {
    if (!ignoreReadonly && cmd.isReadOnly()) {
      releaseLock(null);
      throw new InvalidJsonDbApiUsageException("Failed to modify collection, Collection is loaded as readonly");
    }
    FileLock lock = null;
    try {
      try {
        lock = acquireLock();
      } catch (IOException e) {
        logger.error("Failed to acquire lock for collection file {}", collectionFile.getName(), e);
        return false;
      }

      File tFile;
      try {
        tFile = File.createTempFile(collectionName, null, dbFilesLocation);
      } catch (IOException e) {
        logger.error("Failed to create temporary file for collection {}", collectionName, e);
        return false;
      }

      try (FileOutputStream fos = new FileOutputStream(tFile);
           BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, charset))) {
        writer.write(objectMapper.writeValueAsString(schemaVersion));
        writer.newLine();
        for (Object document : documents) {
          String documentData = objectMapper.writeValueAsString(document);
          writer.write(null == lineTransform ? documentData : lineTransform.apply(documentData));
          writer.newLine();
        }
        writer.flush();
        // Force the data to disk before the rename makes it the collection file, so that a crash or
        // power loss cannot leave an empty or partly written collection file behind
        fos.getFD().sync();
      } catch (JsonProcessingException e) {
        deleteQuietly(tFile);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to write temporary collection file {}", tFile.getName(), e);
        deleteQuietly(tFile);
        return false;
      }

      moveIntoPlace(tFile);
      return true;
    } finally {
      releaseLock(lock);
    }
  }

  /**
   * Forces the folder's entries, and so the rename that replaced the collection file, to disk; see
   * JsonDBConfig#setSyncFolderOnWrite. Opening a folder this way is not supported everywhere (e.g.
   * Windows); the rename is still atomic there, just not yet forced to disk.
   */
  private void syncDirectory(File directory) {
    try (FileChannel folder = FileChannel.open(directory.toPath(), StandardOpenOption.READ)) {
      folder.force(true);
    } catch (IOException | UnsupportedOperationException e) {
      logger.debug("Could not force folder {} to disk after replacing collection file {}", directory, collectionFile.getName(), e);
    }
  }

  private void deleteQuietly(File file) {
    try {
      Files.deleteIfExists(file.toPath());
    } catch (IOException e) {
      logger.error("Failed to delete temporary collection file {}", file.getName(), e);
    }
  }

  /**
   * A utility method that appends the provided object to the end of collection
   * file in a atomic way
   *
   * @param collection existing collection
   * @param objectToSave new Object that is being inserted or updated.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean appendToJsonFile(Collection<T> collection, Object objectToSave) {
    List<Object> documents = new ArrayList<Object>(collection);
    documents.add(objectToSave);
    return writeCollectionFile(documents, false, null);
  }

  /**
   * A utility method that appends the provided collection of objects to the end of collection
   * file in a atomic way
   *
   * @param collection existing collection
   * @param batchToSave collection of objects to append.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean appendToJsonFile(Collection<T> collection, Collection<? extends T> batchToSave) {
    List<Object> documents = new ArrayList<Object>(collection);
    documents.addAll(batchToSave);
    return writeCollectionFile(documents, false, null);
  }

  /**
   * A utility method that substracts the provided Ids and writes rest of the collection to
   * file in a atomic way
   *
   * @param collection existing collection
   * @param id id of objects to be removed.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean removeFromJsonFile(Map<Object, T> collection, Object id) {
    List<Object> documents = new ArrayList<Object>(collection.size());
    for (Entry<Object, T> entry : collection.entrySet()) {
      if (!entry.getKey().equals(id)) {
        documents.add(entry.getValue());
      }
    }
    return writeCollectionFile(documents, false, null);
  }

  /**
   * A utility method that subtracts the provided Ids and writes rest of the collection to
   * file in a atomic way
   *
   * @param collection existing collection
   * @param removeIds ids of objects to be removed.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean removeFromJsonFile(Map<Object, T> collection, Set<Object> removeIds) {
    List<Object> documents = new ArrayList<Object>(collection.size());
    for (Entry<Object, T> entry : collection.entrySet()) {
      if (!removeIds.contains(entry.getKey())) {
        documents.add(entry.getValue());
      }
    }
    return writeCollectionFile(documents, false, null);
  }

  /**
   * A utility method that updates the provided collection of objects into the existing collection
   * file in a atomic way
   *
   * @param collection existing collection
   * @param id the id of object to save
   * @param objectToSave the actual object to save.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean updateInJsonFile(Map<Object, T> collection, Object id, T objectToSave) {
    List<Object> documents = new ArrayList<Object>(collection.size());
    for (Entry<Object, T> entry : collection.entrySet()) {
      documents.add(entry.getKey().equals(id) ? objectToSave : entry.getValue());
    }
    return writeCollectionFile(documents, false, null);
  }

  /**
   * A utility method that updates the provided collection of objects into the existing collection
   * file in a atomic way
   *
   * @param collection existing collection
   * @param modifiedObjects objects to update.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean updateInJsonFile(Map<Object, T> collection, Map<Object, T> modifiedObjects) {
    List<Object> documents = new ArrayList<Object>(collection.size());
    for (Entry<Object, T> entry : collection.entrySet()) {
      documents.add(modifiedObjects.containsKey(entry.getKey()) ? modifiedObjects.get(entry.getKey()) : entry.getValue());
    }
    return writeCollectionFile(documents, false, null);
  }

  /**
   * A utility method that completely replaces the contents of .json with the provided collection
   * in a atomic way
   *
   * @param collection existing collection
   * @param ignoreReadonly force rewrite even if the collection is marked readonly this is necessary for schemaupdate.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   */
  public <T> boolean reWriteJsonFile(Collection<T> collection, boolean ignoreReadonly) {
    return writeCollectionFile(collection, ignoreReadonly, null);
  }

  /**
   * A utility method renames a particular key for the entire contents of .json in a atomic way
   *
   * @param collection existing collection
   * @param ignoreReadonly force rewrite even if the collection is marked readonly this is necessary for schemaupdate.
   * @param oldKey String representing the old key/field
   * @param newKey String representing the new key/field
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if success
   * @deprecated JsonDB no longer uses this: updateCollectionSchema renames properties in the parsed
   *             documents instead (issue #57), because a text replacement can also change a matching
   *             key inside a nested object or a string value. It will be removed in a future release.
   */
  @Deprecated
  public <T> boolean renameKeyInJsonFile(Collection<T> collection, boolean ignoreReadonly, String oldKey, String newKey) {
    // Matching the quotes and colon keeps a value that happens to contain the key name intact. It does
    // break if single quotes (invalid JSON) are used together with JsonParser.Feature.ALLOW_SINGLE_QUOTES
    final String oldKeyWithQuotes = "\"" + oldKey + "\":";
    final String newKeyWithQuotes = "\"" + newKey + "\":";
    return writeCollectionFile(collection, ignoreReadonly, line -> line.replace(oldKeyWithQuotes, newKeyWithQuotes));
  }
}
