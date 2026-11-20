/*
 * Copyright (c) 2026 Farooq Khan
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

  /**
   * @deprecated use {@link #resolveBackupZipFile(Path)}
   */
  @Deprecated
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

  /**
   * @param backupPath a zip file, a folder to put {@value #DEFAULT_BACKUP_FILENAME} in, or a name to add
   *     ".zip" to
   * @return the zip file to back up to
   * @throws IllegalArgumentException if the path is null or empty
   */
  public static Path resolveBackupZipFile(Path backupPath) {
    return resolveZipFile(backupPath, "Backup");
  }

  /**
   * @deprecated use {@link #resolveRestoreZipFile(Path)}
   */
  @Deprecated
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

  /**
   * @param restorePath a zip file, a folder that holds {@value #DEFAULT_BACKUP_FILENAME}, or a name to add
   *     ".zip" to
   * @return the zip file to restore from
   * @throws IllegalArgumentException if the path is null or empty
   */
  public static Path resolveRestoreZipFile(Path restorePath) {
    return resolveZipFile(restorePath, "Restore");
  }

  private static Path resolveZipFile(Path path, String kind) {
    if (null == path || path.toString().trim().isEmpty()) {
      throw new IllegalArgumentException(kind + " path cannot be null or empty");
    }
    if (Files.isDirectory(path)) {
      return path.resolve(DEFAULT_BACKUP_FILENAME);
    }
    String name = path.getFileName().toString();
    if (!name.toLowerCase().endsWith(".zip")) {
      return path.resolveSibling(name + ".zip");
    }
    return path;
  }

  /**
   * @deprecated use {@link #listCollectionJsonFiles(Path)}
   */
  @Deprecated
  public static List<File> listCollectionJsonFiles(File dbDirectory) {
    List<File> files = new ArrayList<File>();
    for (Path file : listCollectionJsonFiles(dbDirectory.toPath())) {
      files.add(file.toFile());
    }
    return files;
  }

  /**
   * @param dbDirectory the database folder
   * @return the collection {@code .json} files in it, none if it cannot be listed
   */
  public static List<Path> listCollectionJsonFiles(Path dbDirectory) {
    List<Path> files = new ArrayList<Path>();
    try (DirectoryStream<Path> listed = Files.newDirectoryStream(dbDirectory)) {
      for (Path file : listed) {
        if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")) {
          files.add(file);
        }
      }
    } catch (IOException e) {
      // Same as File.listFiles() returning null: nothing to list
    }
    return files;
  }

  /**
   * @deprecated use {@link #createBackupZip(Path, Path)}
   */
  @Deprecated
  public static void createBackupZip(File dbDirectory, File zipFile) throws IOException {
    createBackupZip(dbDirectory.toPath(), zipFile.toPath());
  }

  public static void createBackupZip(Path dbDirectory, Path zipFile) throws IOException {
    Path parent = zipFile.getParent();
    if (null != parent) {
      Files.createDirectories(parent);
    }
    List<Path> collectionFiles = listCollectionJsonFiles(dbDirectory);
    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
      for (Path collectionFile : collectionFiles) {
        zos.putNextEntry(new ZipEntry(collectionFile.getFileName().toString()));
        try (InputStream in = Files.newInputStream(collectionFile)) {
          copy(in, zos);
        }
        zos.closeEntry();
      }
    }
  }

  /**
   * @deprecated use {@link #extractReplaceCollectionFiles(Path, Path)}
   */
  @Deprecated
  public static void extractReplaceCollectionFiles(File dbDirectory, File zipFile) throws IOException {
    extractReplaceCollectionFiles(dbDirectory.toPath(), zipFile.toPath());
  }

  public static void extractReplaceCollectionFiles(Path dbDirectory, Path zipFile) throws IOException {
    deleteCollectionJsonFiles(dbDirectory);
    extractToDirectory(dbDirectory, zipFile);
  }

  /**
   * @deprecated use {@link #extractToDirectory(Path, Path)}
   */
  @Deprecated
  public static Map<String, File> extractToDirectory(File targetDirectory, File zipFile) throws IOException {
    Map<String, File> extracted = new LinkedHashMap<String, File>();
    for (Entry<String, Path> entry : extractToDirectory(targetDirectory.toPath(), zipFile.toPath()).entrySet()) {
      extracted.put(entry.getKey(), entry.getValue().toFile());
    }
    return extracted;
  }

  /**
   * Extracts the backup's collection files, skipping entries that are not a plain {@code .json} file name
   *
   * @param targetDirectory the folder to extract to, created if it does not exist
   * @param zipFile the backup
   * @return the extracted files by collection name
   * @throws IOException if the backup cannot be read or a file cannot be written
   */
  public static Map<String, Path> extractToDirectory(Path targetDirectory, Path zipFile) throws IOException {
    Files.createDirectories(targetDirectory);
    Map<String, Path> extracted = new LinkedHashMap<String, Path>();
    try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
      ZipEntry entry = null;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String entryName = entry.getName();
        if (!isSafeCollectionEntry(entryName)) {
          continue;
        }
        Path outputFile = targetDirectory.resolve(entryName);
        try (OutputStream out = Files.newOutputStream(outputFile)) {
          copy(zis, out);
        }
        String collectionName = entryName.substring(0, entryName.length() - ".json".length());
        extracted.put(collectionName, outputFile);
        zis.closeEntry();
      }
    }
    return extracted;
  }

  /**
   * @deprecated use {@link #deleteCollectionJsonFiles(Path)}
   */
  @Deprecated
  public static void deleteCollectionJsonFiles(File dbDirectory) throws IOException {
    deleteCollectionJsonFiles(dbDirectory.toPath());
  }

  public static void deleteCollectionJsonFiles(Path dbDirectory) throws IOException {
    for (Path file : listCollectionJsonFiles(dbDirectory)) {
      Files.deleteIfExists(file);
    }
  }

  /**
   * @deprecated use {@link #collectionNameFromFile(Path)}
   */
  @Deprecated
  public static String collectionNameFromFile(File collectionFile) {
    return collectionNameFromFile(collectionFile.toPath());
  }

  public static String collectionNameFromFile(Path collectionFile) {
    String name = collectionFile.getFileName().toString();
    return name.substring(0, name.length() - ".json".length());
  }

  public static boolean isSafeCollectionEntry(String entryName) {
    if (null == entryName || entryName.contains("..") || entryName.contains("/") || entryName.contains("\\")) {
      return false;
    }
    return entryName.endsWith(".json");
  }

  /**
   * @deprecated use {@link #copyCollectionFile(Path, Path)}
   */
  @Deprecated
  public static void copyCollectionFile(File source, File target) throws IOException {
    copyCollectionFile(source.toPath(), target.toPath());
  }

  /**
   * Copies a collection file, also from one file system to another
   *
   * @param source the file to copy
   * @param target the file to create or replace
   * @throws IOException if the copy fails
   */
  public static void copyCollectionFile(Path source, Path target) throws IOException {
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    int read = 0;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
  }
}
