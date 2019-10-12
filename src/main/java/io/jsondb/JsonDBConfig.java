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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Comparator;

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
  private File dbFilesLocation;
  private Path dbFilesPath;
  private String baseScanPackage;
  private ICipher cipher;
  private boolean compatibilityMode;

  //References
  private ObjectMapper objectMapper;
  private Comparator<String> schemaComparator;

  public JsonDBConfig(String dbFilesLocationString, String baseScanPackage,
      ICipher cipher, boolean compatibilityMode, Comparator<String> schemaComparator) {

    this.charset = Charset.forName("UTF-8");
    this.dbFilesLocationString = dbFilesLocationString;
    this.dbFilesLocation = new File(dbFilesLocationString);
    this.dbFilesPath = dbFilesLocation.toPath();
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
    this.dbFilesLocation = new File(dbFilesLocationString);
    this.dbFilesPath = dbFilesLocation.toPath();
  }
  public File getDbFilesLocation() {
    return dbFilesLocation;
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
