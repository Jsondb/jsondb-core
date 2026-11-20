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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.io.JsonDbArchive;
import io.jsondb.tests.model.Instance;

/**
 * backup() and restore() with Paths, for a database and backups on a file system other than the default
 * one, here Jimfs (issue #17)
 */
public class JimfsBackupRestoreTests {
  private static final String CIPHER_KEY = "1r8+24pibarAWgS85/Heeg==";

  private FileSystem fs;
  private Path dbFilesPath;
  private Path backupZip;
  private JsonDBTemplate template;
  private List<Path> diskPaths = new ArrayList<Path>();

  @Before
  public void setUp() throws Exception {
    fs = Jimfs.newFileSystem(Configuration.unix());
    dbFilesPath = fs.getPath("/db");
    backupZip = fs.getPath("/backups/test-backup.zip");
    Files.createDirectories(dbFilesPath);
    Files.copy(Paths.get("src/test/resources/dbfiles/instances.json"), dbFilesPath.resolve("instances.json"));
    template = new JsonDBTemplate(dbFilesPath, "io.jsondb.tests.model", new DefaultAESCBCCipher(CIPHER_KEY));
  }

  @After
  public void tearDown() throws Exception {
    template.close();
    fs.close();
    for (Path path : diskPaths) {
      Util.delete(path);
    }
  }

  private List<String> namesIn(Path folder) throws Exception {
    try (Stream<Path> listed = Files.list(folder)) {
      return listed.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
    }
  }

  @Test
  public void restoreReplaceRecoversDeletedData() throws Exception {
    int count = template.findAll(Instance.class).size();
    template.backup(backupZip);
    assertTrue(Files.isRegularFile(backupZip));

    template.remove(template.findById("01", Instance.class), Instance.class);
    template.restore(backupZip, false);

    assertEquals(count, template.findAll(Instance.class).size());
    assertEquals("ec2-54-191-01", template.findById("01", Instance.class).getHostname());
  }

  /**
   * Merging extracts the backup to a temporary folder, which must be on the database's file system and
   * gone afterwards
   */
  @Test
  public void restoreMergeAddsBackRemovedDocumentsAndLeavesNoTemporaryFolder() throws Exception {
    String privateKey = template.findById("01", Instance.class).getPrivateKey();
    template.backup(backupZip);
    template.remove(template.findById("01", Instance.class), Instance.class);

    template.restore(backupZip, true);

    assertEquals(privateKey, template.findById("01", Instance.class).getPrivateKey());
    assertEquals("[instances.json, lock]", namesIn(dbFilesPath).toString());
  }

  @Test
  public void backupToAFolderUsesTheDefaultName() throws Exception {
    Path folder = fs.getPath("/backups");
    Files.createDirectories(folder);

    template.backup(folder);

    assertTrue(Files.isRegularFile(folder.resolve(JsonDbArchive.DEFAULT_BACKUP_FILENAME)));
    template.restore(folder, false);
  }

  @Test
  public void backupWithoutZipExtensionAddsIt() throws Exception {
    template.backup(fs.getPath("/backups/nightly"));

    assertTrue(Files.isRegularFile(fs.getPath("/backups/nightly.zip")));
    template.restore(fs.getPath("/backups/nightly"), false);
  }

  /**
   * A database in memory backed up to disk, and that backup restored into a database on disk
   */
  @Test
  public void backupCrossesFileSystems() throws Exception {
    Path diskFolder = Paths.get("src/test/resources/dbfiles/jimfsBackupRestoreTests-" + System.nanoTime());
    diskPaths.add(diskFolder);
    Path diskZip = diskFolder.resolve("backups/from-memory.zip");

    template.backup(diskZip);

    assertTrue(Files.isRegularFile(diskZip));
    try (JsonDBTemplate onDisk = new JsonDBTemplate(diskFolder.resolve("db"), "io.jsondb.tests.model",
        new DefaultAESCBCCipher(CIPHER_KEY))) {
      onDisk.restore(diskZip, true);
      assertEquals(template.findAll(Instance.class).size(), onDisk.findAll(Instance.class).size());
      assertEquals(template.findById("01", Instance.class).getPrivateKey(),
          onDisk.findById("01", Instance.class).getPrivateKey());
    }

    int count = template.findAll(Instance.class).size();
    template.remove(template.findById("01", Instance.class), Instance.class);
    template.restore(diskZip, false);
    assertEquals(count, template.findAll(Instance.class).size());
  }

  @Test
  public void restoreOfAMissingBackupIsRejected() throws Exception {
    try {
      template.restore(fs.getPath("/backups/missing.zip"), false);
      fail("Expected a missing backup to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("/backups/missing.zip"));
    }
  }

  @Test
  public void nullPathsAreRejected() throws Exception {
    try {
      template.backup((Path) null);
      fail("Expected a null backup path to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      // Rejecting is the correct outcome
    }
    try {
      template.restore((Path) null, false);
      fail("Expected a null restore path to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      // Rejecting is the correct outcome
    }
  }
}
