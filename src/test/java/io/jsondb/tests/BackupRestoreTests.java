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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.Default1Cipher;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.io.JsonDbArchive;
import io.jsondb.query.Update;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;
import io.jsondb.tests.util.TestUtils;

public class BackupRestoreTests {

  private static final String DB = "src/test/resources/dbfiles/backupRestoreTests";
  private static final String BACKUP_DIR = "src/test/resources/dbfiles/backupRestoreTests-backups";

  private File dbFolder = new File(DB);
  private File backupFolder = new File(BACKUP_DIR);
  private File instancesJson = new File(dbFolder, "instances.json");
  private File backupZip = new File(backupFolder, "test-backup.zip");

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    dbFolder.mkdirs();
    backupFolder.mkdirs();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    if (backupZip.exists()) {
      backupZip.delete();
    }
  }

  @After
  public void tearDown() {
    Util.delete(dbFolder);
    Util.delete(backupFolder);
  }

  @Test
  public void testBackupCreatesZipWithCollectionFiles() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    assertEquals(6, template.findAll(Instance.class).size());

    template.backup(backupZip.getAbsolutePath());

    assertTrue(backupZip.isFile());
    ZipFile zipFile = new ZipFile(backupZip);
    try {
      boolean foundInstances = false;
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if ("instances.json".equals(entry.getName())) {
          foundInstances = true;
        }
      }
      assertTrue(foundInstances);
    } finally {
      zipFile.close();
    }
  }

  @Test
  public void testRestoreReplaceRecoversDeletedData() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    template.backup(backupZip.getAbsolutePath());

    Instance toRemove = template.findById("06", Instance.class);
    template.remove(toRemove, Instance.class);
    assertEquals(5, template.findAll(Instance.class).size());

    template.restore(backupZip.getAbsolutePath(), false);
    assertEquals(6, template.findAll(Instance.class).size());
    assertNotNull(template.findById("06", Instance.class));
  }

  @Test
  public void testRestoreMergeUpdatesExistingAndAddsMissingRecords() throws Exception {
    JsonDBTemplate source = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    source.backup(backupZip.getAbsolutePath());

    JsonDBTemplate target = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    Instance toRemoveFromTarget = target.findById("06", Instance.class);
    target.remove(toRemoveFromTarget, Instance.class);
    Update update = Update.update("hostname", "mutated-before-merge");
    target.findAndModify(String.format("/.[id='%s']", "03"), update, Instance.class);
    assertEquals(5, target.findAll(Instance.class).size());
    assertEquals("mutated-before-merge", target.findById("03", Instance.class).getHostname());

    target.restore(backupZip.getAbsolutePath(), true);

    assertEquals(6, target.findAll(Instance.class).size());
    assertNotNull(target.findById("06", Instance.class));
    assertEquals("ec2-54-191-04", target.findById("03", Instance.class).getHostname());
  }

  @Test
  public void testRestoreMergeAddsCollectionMissingFromDatabase() throws Exception {
    JsonDBTemplate source = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    source.createCollection(Site.class);
    Site site = new Site();
    site.setId("site-1");
    site.setLocation("eu-west-1");
    source.insert(site, "sites");
    source.backup(backupZip.getAbsolutePath());

    new File(dbFolder, "sites.json").delete();
    JsonDBTemplate target = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    assertFalse(target.collectionExists(Site.class));

    target.restore(backupZip.getAbsolutePath(), true);

    assertTrue(target.collectionExists(Site.class));
    List<Site> sites = target.findAll(Site.class);
    assertEquals(1, sites.size());
    assertEquals("site-1", sites.get(0).getId());
    assertEquals("eu-west-1", sites.get(0).getLocation());
  }

  @Test
  public void testRestoreReplaceRemovesCollectionsNotPresentInBackup() throws Exception {
    JsonDBTemplate source = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    source.backup(backupZip.getAbsolutePath());

    JsonDBTemplate target = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    target.createCollection(Site.class);
    Site site = new Site();
    site.setId("site-1");
    site.setLocation("eu-west-1");
    target.insert(site, "sites");
    assertTrue(target.collectionExists(Site.class));

    target.restore(backupZip.getAbsolutePath(), false);

    assertFalse(target.collectionExists(Site.class));
    assertFalse(new File(dbFolder, "sites.json").exists());
    assertEquals(6, target.findAll(Instance.class).size());
  }

  @Test
  public void testBackupToDirectoryUsesDefaultZipName() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    template.backup(backupFolder.getAbsolutePath());

    File defaultBackup = new File(backupFolder, JsonDbArchive.DEFAULT_BACKUP_FILENAME);
    assertTrue(defaultBackup.isFile());
  }

  @Test
  public void testRestoreMissingBackupThrows() {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Restore backup file not found");
    template.restore(new File(backupFolder, "missing.zip").getAbsolutePath(), false);
  }

  @Test
  public void testBackupEmptyPathThrows() {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    template.backup("");
  }

  @Test
  public void testRoundTripPreservesCollectionFileContents() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    template.backup(backupZip.getAbsolutePath());

    Util.delete(dbFolder);
    dbFolder.mkdirs();

    template.restore(backupZip.getAbsolutePath(), false);

    assertEquals(7, TestUtils.getNoOfLinesInFile(instancesJson));
  }

  /**
   * Backup files hold @Secret fields already encrypted. Merging them back must not encrypt them a
   * second time, or the restored secrets come back as ciphertext.
   */
  @Test
  public void testRestoreMergeOnEncryptedDatabaseKeepsSecretsReadable() throws Exception {
    ICipher cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model", cipher);
    assertEquals("b87eb02f5dd7e5232d7b0fc30a5015e4", template.findById("01", Instance.class).getPrivateKey());
    template.backup(backupZip.getAbsolutePath());

    Instance changed = template.findById("01", Instance.class);
    changed.setPrivateKey("changed-after-backup");
    template.save(changed, Instance.class);

    template.restore(backupZip.getAbsolutePath(), true);

    assertEquals("b87eb02f5dd7e5232d7b0fc30a5015e4", template.findById("01", Instance.class).getPrivateKey());
    assertEquals("b87eb02f5dd7e5232d7b0fc30a5015e4", template.findById("02", Instance.class).getPrivateKey());
    template.reLoadDB();
    assertEquals("b87eb02f5dd7e5232d7b0fc30a5015e4", template.findById("01", Instance.class).getPrivateKey());
  }

  @Test
  public void testRestoreMergeWithDefault1CipherKeepsSecretsReadable() throws Exception {
    Util.delete(instancesJson);
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model", new Default1Cipher("1r8+24pibarAWgS85/Heeg=="));
    template.createCollection(Instance.class);
    Instance instance = new Instance();
    instance.setId("07");
    instance.setPrivateKey("original-secret");
    template.insert(instance);
    template.backup(backupZip.getAbsolutePath());

    template.findAndModify("/.[id='07']", Update.update("privateKey", "changed-after-backup"), Instance.class);
    template.restore(backupZip.getAbsolutePath(), true);

    assertEquals("original-secret", template.findById("07", Instance.class).getPrivateKey());
  }

  /**
   * Writes a zip with the given entry names and contents. A null content makes a directory entry.
   */
  private void writeZip(File zip, String[][] entries) throws Exception {
    zip.getParentFile().mkdirs();
    try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
      for (String[] entry : entries) {
        out.putNextEntry(new ZipEntry(entry[0]));
        if (null != entry[1]) {
          out.write(entry[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        out.closeEntry();
      }
    }
  }

  @Test
  public void testRestoreFromADirectoryUsesTheDefaultBackupFile() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    template.backup(BACKUP_DIR);
    template.remove(template.findById("01", Instance.class), Instance.class);
    assertEquals(5, template.findAll(Instance.class).size());

    template.restore(BACKUP_DIR, false);
    assertEquals(6, template.findAll(Instance.class).size());
  }

  @Test
  public void testRestorePathWithoutZipExtensionAddsIt() throws Exception {
    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    String path = new File(backupFolder, "nightly").getPath();
    template.backup(path);
    assertTrue(new File(backupFolder, "nightly.zip").isFile());
    template.remove(template.findById("01", Instance.class), Instance.class);

    template.restore(path, false);
    assertEquals(6, template.findAll(Instance.class).size());
  }

  /**
   * A crafted backup must not be able to write outside the database folder (zip slip), create
   * subfolders, or drop files that are not collection files, in either restore mode
   */
  @Test
  public void testRestoreIgnoresUnsafeZipEntries() throws Exception {
    String instances = new String(java.nio.file.Files.readAllBytes(instancesJson.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    File escaped = new File(dbFolder.getParentFile(), "escaped.json");
    writeZip(backupZip, new String[][] {
        {"instances.json", instances},
        {"../escaped.json", instances},
        {"nested/inner.json", instances},
        {"folder/", null},
        {"readme.txt", "not a collection"}});
    try {
      for (boolean merge : new boolean[] {false, true}) {
        JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
        template.restore(backupZip.getAbsolutePath(), merge);
        assertEquals(6, template.findAll(Instance.class).size());
        assertFalse("entry escaped the database folder (merge=" + merge + ")", escaped.exists());
        assertFalse("nested entry created a folder (merge=" + merge + ")", new File(dbFolder, "nested").exists());
        assertFalse("directory entry created a folder (merge=" + merge + ")", new File(dbFolder, "folder").exists());
        assertFalse("non-collection file restored (merge=" + merge + ")", new File(dbFolder, "readme.txt").exists());
        template.close();
      }
    } finally {
      escaped.delete();
    }
  }

  /**
   * Merge mode copies a collection file that no class of this database knows as it is, so the data is
   * kept for a class added later
   */
  @Test
  public void testRestoreMergeKeepsCollectionsThisDatabaseDoesNotKnow() throws Exception {
    String strangers = "{\"schemaVersion\":\"1.0\"}\n{\"id\":\"s1\",\"name\":\"kept\"}\n";
    writeZip(backupZip, new String[][] {{"strangers.json", strangers}});

    JsonDBTemplate template = new JsonDBTemplate(DB, "io.jsondb.tests.model");
    template.restore(backupZip.getAbsolutePath(), true);

    File restored = new File(dbFolder, "strangers.json");
    assertTrue(restored.isFile());
    assertEquals(strangers, new String(java.nio.file.Files.readAllBytes(restored.toPath()), java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(6, template.findAll(Instance.class).size());
  }
}
