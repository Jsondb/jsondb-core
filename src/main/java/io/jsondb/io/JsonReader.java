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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsondb.JsonDBConfig;

/**
 * A special File Reader to read the .json DB files that ensures
 * proper character encoding is used and the necessary File Locks are created.
 *
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class JsonReader {

  private Logger logger = LoggerFactory.getLogger(JsonReader.class);

  private Path collectionFile;

  private InputStream fis;
  private FileChannel channel;
  private InputStreamReader isr;
  private BufferedReader reader;
  private FileLock lock;
  private Path lockFilesLocation;
  private Path fileLockLocation;

  /**
   * @deprecated use {@link #JsonReader(JsonDBConfig, Path)}
   */
  @Deprecated
  public JsonReader(JsonDBConfig dbConfig, File collectionFile) throws IOException {
    this(dbConfig, collectionFile.toPath(), true);
  }

  /**
   * @deprecated use {@link #JsonReader(JsonDBConfig, Path, boolean)}
   */
  @Deprecated
  public JsonReader(JsonDBConfig dbConfig, File collectionFile, boolean takeLock) throws IOException {
    this(dbConfig, collectionFile.toPath(), takeLock);
  }

  public JsonReader(JsonDBConfig dbConfig, Path collectionFile) throws IOException {
    this(dbConfig, collectionFile, true);
  }

  /**
   * @param takeLock false to read without a lock file, for a database opened read-only. Only safe
   *     because writers move a complete new file into place, so a reader sees the old file or the new one.
   */
  public JsonReader(JsonDBConfig dbConfig, Path collectionFile, boolean takeLock) throws IOException {
    this.collectionFile = collectionFile;
    CharsetDecoder decoder = dbConfig.getCharset().newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPORT);
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    if (!takeLock) {
      fis = Files.newInputStream(collectionFile);
      isr = new InputStreamReader(fis, decoder);
      reader = new BufferedReader(isr);
      return;
    }

    this.lockFilesLocation = collectionFile.resolveSibling("lock");
    this.fileLockLocation = lockFilesLocation.resolve(collectionFile.getFileName() + ".lock");

    Files.createDirectories(lockFilesLocation);
    channel = FileChannel.open(fileLockLocation, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    try {
      lock = channel.lock();
    } catch (IOException | OverlappingFileLockException e) {
      try {
        channel.close();
      } catch (IOException e1) {
        logger.error("Failed while closing lock file channel for collection file {}", collectionFile.getFileName());
      }
      throw new JsonFileLockException("JsonReader failed to obtain a file lock for file " + fileLockLocation, e);
    }

    try {
      fis = Files.newInputStream(collectionFile);
    } catch (IOException e) {
      // Release the file lock, otherwise every later reader and writer of this collection in the JVM fails
      try {
        lock.release();
        channel.close();
      } catch (IOException e1) {
        logger.error("Failed to release lock for collection file {}", collectionFile.getFileName(), e1);
      }
      throw e;
    }
    isr = new InputStreamReader(fis, decoder);
    reader = new BufferedReader(isr);
  }
  
  /**
   * A utility method that reads the next line and returns it.
   * Since we use a BufferedReader this method may often read more
   * than the next line to determine if the line ended.
   * @return the content of the line just read
   * @throws IOException if an I/O error occurs
   */
  public String readLine() throws IOException {
    return reader.readLine();
  }

  public void close() {
    try {
      reader.close();
    } catch (IOException e) {
      logger.error("Failed to close BufferedReader for collection file {}", collectionFile.getFileName(), e);
    }
    try {
      isr.close();
    } catch (IOException e) {
      logger.error("Failed to close InputStreamReader for collection file {}", collectionFile.getFileName(), e);
    }
    try {
      fis.close();
    } catch (IOException e) {
      logger.error("Failed to close InputStream for collection file {}", collectionFile.getFileName(), e);
    }
    if (lock == null) {
      // Read without a lock file, so there is no lock or channel to close
      return;
    }
    try {
      if(lock.isValid()) {
        lock.release();
      }
    } catch (IOException e) {
      logger.error("Failed to release lock for collection file {}", collectionFile.getFileName(), e);
    }
    try {
      channel.close();
    } catch (IOException e) {
      logger.error("Failed to close FileChannel for collection file {}", collectionFile.getFileName(), e);
    }
  }
}
