package io.jsondb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

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
}
