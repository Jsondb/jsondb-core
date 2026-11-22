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
package io.jsondb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.jsondb.Util;
import io.jsondb.io.JsonDbArchive;

public class JsonDbArchiveTests {

  @Test
  public void testResolveBackupZipFileAppendsExtension() {
    File zipFile = JsonDbArchive.resolveBackupZipFile("/tmp/my-backup");
    assertEquals("my-backup.zip", zipFile.getName());
  }

  @Test
  public void testResolveBackupZipFileUsesDefaultNameInDirectory() {
    File directory = new File("target/jsondb-backup-dir-test");
    directory.mkdirs();
    try {
      File zipFile = JsonDbArchive.resolveBackupZipFile(directory.getAbsolutePath());
      assertEquals(JsonDbArchive.DEFAULT_BACKUP_FILENAME, zipFile.getName());
      assertEquals(directory.getAbsolutePath(), zipFile.getParentFile().getAbsolutePath());
    } finally {
      directory.delete();
    }
  }

  @Test
  public void testResolveRestoreZipFileKeepsZipExtension() {
    File zipFile = JsonDbArchive.resolveRestoreZipFile("/tmp/archive.zip");
    assertEquals("archive.zip", zipFile.getName());
  }

  @Test
  public void testIsSafeCollectionEntryRejectsPathTraversal() {
    assertFalse(JsonDbArchive.isSafeCollectionEntry("../instances.json"));
    assertFalse(JsonDbArchive.isSafeCollectionEntry("nested/instances.json"));
    assertTrue(JsonDbArchive.isSafeCollectionEntry("instances.json"));
  }

  @Test
  public void testResolveBackupZipPathOnAnotherFileSystem() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path folder = Files.createDirectories(fs.getPath("/backups"));
      assertEquals(folder.resolve(JsonDbArchive.DEFAULT_BACKUP_FILENAME), JsonDbArchive.resolveBackupZipFile(folder));
      assertEquals(fs.getPath("/backups/my-backup.zip"), JsonDbArchive.resolveBackupZipFile(fs.getPath("/backups/my-backup")));
      assertEquals(fs.getPath("/backups/archive.ZIP"), JsonDbArchive.resolveRestoreZipFile(fs.getPath("/backups/archive.ZIP")));
      assertEquals(fs.getPath("my-backup.zip"), JsonDbArchive.resolveRestoreZipFile(fs.getPath("my-backup")));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testResolveBackupZipPathRejectsAnEmptyPath() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      JsonDbArchive.resolveBackupZipFile(fs.getPath(""));
    }
  }

  @Test
  public void testListCollectionJsonFilesListsOnlyJsonFiles() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path db = Files.createDirectories(fs.getPath("/db"));
      Files.createFile(db.resolve("instances.json"));
      Files.createFile(db.resolve("notes.txt"));
      Files.createDirectories(db.resolve("folder.json"));
      List<Path> listed = JsonDbArchive.listCollectionJsonFiles(db);
      assertEquals(1, listed.size());
      assertEquals("instances", JsonDbArchive.collectionNameFromFile(listed.get(0)));
      assertTrue(JsonDbArchive.listCollectionJsonFiles(fs.getPath("/nowhere")).isEmpty());
    }
  }

  /**
   * The deprecated File versions still work, they hand over to the Path ones
   */
  @Test
  @SuppressWarnings("deprecation")
  public void testFileVersionsRoundTrip() throws Exception {
    File root = new File("src/test/resources/dbfiles/jsonDbArchiveTests-" + System.nanoTime());
    File db = new File(root, "db");
    File extracted = new File(root, "extracted");
    File zip = new File(root, "backups/backup.zip");
    assertTrue(db.mkdirs());
    try {
      Files.copy(Paths.get("src/test/resources/dbfiles/instances.json"), new File(db, "instances.json").toPath());

      JsonDbArchive.createBackupZip(db, zip);
      Map<String, File> files = JsonDbArchive.extractToDirectory(extracted, zip);
      assertEquals(new File(extracted, "instances.json"), files.get("instances"));
      assertEquals("instances", JsonDbArchive.collectionNameFromFile(files.get("instances")));

      JsonDbArchive.deleteCollectionJsonFiles(db);
      assertTrue(JsonDbArchive.listCollectionJsonFiles(db).isEmpty());
      JsonDbArchive.copyCollectionFile(files.get("instances"), new File(db, "instances.json"));
      assertEquals(1, JsonDbArchive.listCollectionJsonFiles(db).size());
      JsonDbArchive.extractReplaceCollectionFiles(db, zip);
      assertTrue(Files.readAllBytes(new File(db, "instances.json").toPath()).length > 0);
    } finally {
      Util.delete(root.toPath());
    }
  }
}
