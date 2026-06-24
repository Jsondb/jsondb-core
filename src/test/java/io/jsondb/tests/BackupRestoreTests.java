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
}
