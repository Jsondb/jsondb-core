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
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsondb.CollectionMetaData;
import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBConfig;
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
    if (cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();
        
        for (T o : collection) {
          String documentData = objectMapper.writeValueAsString(o);
          writer.write(documentData);
          writer.newLine();
        }
        String newDocument = objectMapper.writeValueAsString(objectToSave);
        writer.write(newDocument);
        writer.newLine();
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
    if (cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();
        
        for (T o : collection) {
          String documentData = objectMapper.writeValueAsString(o);
          writer.write(documentData);
          writer.newLine();
        }
        for (T o : batchToSave) {
          String documentData = objectMapper.writeValueAsString(o);
          writer.write(documentData);
          writer.newLine();
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
    if (cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();
        
        for (Entry<Object, T> entry : collection.entrySet()) {
          if (!entry.getKey().equals(id)) {
          String documentData = objectMapper.writeValueAsString(entry.getValue());
          writer.write(documentData);
          writer.newLine();
          }
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
    if (cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();
        
        for (Entry<Object, T> entry : collection.entrySet()) {
          if (!removeIds.contains(entry.getKey())) {
          String documentData = objectMapper.writeValueAsString(entry.getValue());
          writer.write(documentData);
          writer.newLine();
          }
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
    if (cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();
        
        for (Entry<Object, T> entry : collection.entrySet()) {
          T o = null;
          if (entry.getKey().equals(id)) {
            o = objectToSave;
          } else {
            o = entry.getValue();
          }

          String documentData = objectMapper.writeValueAsString(o);
          writer.write(documentData);
          writer.newLine();
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
    if (cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();

        for (Entry<Object, T> entry : collection.entrySet()) {
          T o = null;
          if (modifiedObjects.containsKey(entry.getKey())) {
            o = modifiedObjects.get(entry.getKey());
          } else {
            o = entry.getValue();
          }

          String documentData = objectMapper.writeValueAsString(o);
          writer.write(documentData);
          writer.newLine();
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
    if (!ignoreReadonly && cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();

        for (T o : collection) {
          String documentData = objectMapper.writeValueAsString(o);
          writer.write(documentData);
          writer.newLine();
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
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
   */
  public <T> boolean renameKeyInJsonFile(Collection<T> collection, boolean ignoreReadonly, String oldKey, String newKey) {
    if (!ignoreReadonly && cmd.isReadOnly()) {
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
        logger.error("Failed to create temporary file for append", e);
        return false;
      }
      String tFileName = tFile.getName();

      FileOutputStream fos = null;
      OutputStreamWriter osr = null;
      BufferedWriter writer = null;
      try {
        fos = new FileOutputStream(tFile);
        osr = new OutputStreamWriter(fos, charset);
        writer = new BufferedWriter(osr);

        //Stamp version first
        String version = objectMapper.writeValueAsString(schemaVersion);
        writer.write(version);
        writer.newLine();

        //We do the below so that we do not coincidentally replace contents of some value
        //This does cause a problem it will break if single quotes(invalid) is used along with the
        //JsonParser.Feature.ALLOW_SINGLE_QUOTES
        String oldKeyWithQuotes = "\"" + oldKey + "\":";
        String newKeyWithQuotes = "\"" + newKey + "\":";
        
        for (T o : collection) {
          String documentData = objectMapper.writeValueAsString(o);
          documentData = documentData.replace(oldKeyWithQuotes, newKeyWithQuotes);
          writer.write(documentData);
          writer.newLine();
        }
      } catch (JsonProcessingException e) {
        logger.error("Failed in coverting Object to Json collection {}", collectionName, e);
        throw new InvalidJsonDbApiUsageException("Failed Json Processing for collection " + collectionName, e);
      } catch (IOException e) {
        logger.error("Failed to append object to temporary collection file {}", tFileName, e);
        return false;
      } finally {
        try {
          writer.close();
        } catch (IOException e) {
          logger.error("Failed to close BufferedWriter for temporary collection file {}", tFileName, e);
        }
        try {
          osr.close();
        } catch (IOException e) {
          logger.error("Failed to close OutputStreamWriter for temporary collection file {}", tFileName, e);
        }
        try {
          fos.close();
        } catch (IOException e) {
          logger.error("Failed to close FileOutputStream for temporary collection file {}", tFileName, e);
        }
      }

      try {
        Files.move(tFile.toPath(), collectionFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        logger.error("Failed to move temporary collection file {} to collection file {}", tFileName, collectionFile.getName(), e);
      }
      return true;
      
    } finally {
      releaseLock(lock);
    }
  }
}
