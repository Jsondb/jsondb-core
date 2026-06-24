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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility for creating and reading JsonDB zip backups.
 * Each backup is a zip archive containing collection {@code *.json} files at the root level.
 */
public final class JsonDbArchive {

  public static final String DEFAULT_BACKUP_FILENAME = "jsondb-backup.zip";

  private JsonDbArchive() {
  }

  public static File resolveBackupZipFile(String backupPath) {
    if (null == backupPath || backupPath.trim().isEmpty()) {
      throw new IllegalArgumentException("Backup path cannot be null or empty");
    }
    File path = new File(backupPath);
    if (path.isDirectory()) {
      return new File(path, DEFAULT_BACKUP_FILENAME);
    }
    if (!backupPath.toLowerCase().endsWith(".zip")) {
      return new File(backupPath + ".zip");
    }
    return path;
  }

  public static File resolveRestoreZipFile(String restorePath) {
    if (null == restorePath || restorePath.trim().isEmpty()) {
      throw new IllegalArgumentException("Restore path cannot be null or empty");
    }
    File path = new File(restorePath);
    if (path.isDirectory()) {
      path = new File(path, DEFAULT_BACKUP_FILENAME);
    } else if (!restorePath.toLowerCase().endsWith(".zip")) {
      path = new File(restorePath + ".zip");
    }
    return path;
  }

  public static List<File> listCollectionJsonFiles(File dbDirectory) {
    List<File> files = new ArrayList<File>();
    File[] listed = dbDirectory.listFiles();
    if (null != listed) {
      for (File file : listed) {
        if (file.isFile() && file.getName().endsWith(".json")) {
          files.add(file);
        }
      }
    }
    return files;
  }

  public static void createBackupZip(File dbDirectory, File zipFile) throws IOException {
    File parent = zipFile.getParentFile();
    if (null != parent) {
      parent.mkdirs();
    }
    List<File> collectionFiles = listCollectionJsonFiles(dbDirectory);
    ZipOutputStream zos = null;
    try {
      zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
      byte[] buffer = new byte[8192];
      for (File collectionFile : collectionFiles) {
        ZipEntry entry = new ZipEntry(collectionFile.getName());
        zos.putNextEntry(entry);
        FileInputStream fis = null;
        try {
          fis = new FileInputStream(collectionFile);
          int read = 0;
          while ((read = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, read);
          }
        } finally {
          if (null != fis) {
            fis.close();
          }
        }
        zos.closeEntry();
      }
    } finally {
      if (null != zos) {
        zos.close();
      }
    }
  }

  public static void extractReplaceCollectionFiles(File dbDirectory, File zipFile) throws IOException {
    deleteCollectionJsonFiles(dbDirectory);
    extractCollectionFiles(dbDirectory, zipFile);
  }

  public static Map<String, File> extractToDirectory(File targetDirectory, File zipFile) throws IOException {
    targetDirectory.mkdirs();
    Map<String, File> extracted = new LinkedHashMap<String, File>();
    ZipInputStream zis = null;
    try {
      zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
      ZipEntry entry = null;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String entryName = entry.getName();
        if (!isSafeCollectionEntry(entryName)) {
          continue;
        }
        File outputFile = new File(targetDirectory, entryName);
        FileOutputStream fos = null;
        try {
          fos = new FileOutputStream(outputFile);
          byte[] buffer = new byte[8192];
          int read = 0;
          while ((read = zis.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
          }
        } finally {
          if (null != fos) {
            fos.close();
          }
        }
        String collectionName = entryName.substring(0, entryName.length() - ".json".length());
        extracted.put(collectionName, outputFile);
        zis.closeEntry();
      }
    } finally {
      if (null != zis) {
        zis.close();
      }
    }
    return extracted;
  }

  public static void deleteCollectionJsonFiles(File dbDirectory) throws IOException {
    for (File file : listCollectionJsonFiles(dbDirectory)) {
      Files.deleteIfExists(file.toPath());
    }
  }

  public static String collectionNameFromFile(File collectionFile) {
    String name = collectionFile.getName();
    return name.substring(0, name.length() - ".json".length());
  }

  private static void extractCollectionFiles(File dbDirectory, File zipFile) throws IOException {
    ZipInputStream zis = null;
    try {
      zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
      ZipEntry entry = null;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String entryName = entry.getName();
        if (!isSafeCollectionEntry(entryName)) {
          continue;
        }
        File outputFile = new File(dbDirectory, entryName);
        FileOutputStream fos = null;
        try {
          fos = new FileOutputStream(outputFile);
          byte[] buffer = new byte[8192];
          int read = 0;
          while ((read = zis.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
          }
        } finally {
          if (null != fos) {
            fos.close();
          }
        }
        zis.closeEntry();
      }
    } finally {
      if (null != zis) {
        zis.close();
      }
    }
  }

  public static boolean isSafeCollectionEntry(String entryName) {
    if (null == entryName || entryName.contains("..") || entryName.contains("/") || entryName.contains("\\")) {
      return false;
    }
    return entryName.endsWith(".json");
  }

  public static void copyCollectionFile(File source, File target) throws IOException {
    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }
}
