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
package io.jsondb.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.ReflectionPropertyHandler;
import io.jsondb.crypto.CryptoUtil;
import io.jsondb.crypto.Default1Cipher;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.query.Update;
import io.jsondb.android.model.Device;

/**
 * Exercises jsondb-core on a real Android runtime (ART), where there is no java.beans and package
 * scanning cannot see application classes.
 */
@RunWith(AndroidJUnit4.class)
public class JsonDbOnAndroidTest {
  private static final String TAG = "JsonDbOnAndroidTest";
  private static final Collection<Class<?>> ENTITIES = Collections.<Class<?>>singletonList(Device.class);

  private File dbDir;

  @Before
  public void setUp() {
    File filesDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
    dbDir = new File(filesDir, "jsondb-" + System.nanoTime());
    assertTrue(dbDir.mkdirs());
  }

  @After
  public void tearDown() {
    delete(dbDir);
  }

  private static void delete(File f) {
    File[] children = f.listFiles();
    if (null != children) {
      for (File c : children) {
        delete(c);
      }
    }
    f.delete();
  }

  private static Device device(String id, String name, int port, boolean active, Device.Status status, String city,
      List<String> tags) {
    Device d = new Device();
    d.setId(id);
    d.setName(name);
    d.setPort(port);
    d.setActive(active);
    d.setStatus(status);
    if (null != city) {
      Device.Location location = new Device.Location();
      location.setCity(city);
      d.setLocation(location);
    }
    d.setTags(tags);
    return d;
  }

  private JsonDBTemplate templateWithData() {
    JsonDBTemplate db = new JsonDBTemplate(dbDir.getAbsolutePath(), ENTITIES);
    db.createCollection(Device.class);
    db.insert(Arrays.asList(
        device("1", "a", 8080, true, Device.Status.ACTIVE, "Pune", Arrays.asList("x", "y")),
        device("2", "b", 80, false, Device.Status.RETIRED, "Mumbai", Arrays.asList("y")),
        device("3", null, 9000, true, Device.Status.ACTIVE, null, null),
        device("4", "c", 443, false, Device.Status.ACTIVE, "Pune", Collections.<String>emptyList())), Device.class);
    return db;
  }

  private static List<String> ids(List<Device> devices) {
    List<String> ids = new ArrayList<String>();
    for (Device d : devices) {
      ids.add(d.getId());
    }
    Collections.sort(ids);
    return ids;
  }

  @Test
  public void testJavaBeansIsMissingOnAndroid() {
    assertFalse(ReflectionPropertyHandler.isJavaBeansAvailable());
  }

  @Test
  public void testPackageScanningFindsNoClassesOnAndroid() {
    try {
      JsonDBTemplate db = new JsonDBTemplate(dbDir.getAbsolutePath(), "io.jsondb.android.model");
      db.createCollection(Device.class);
      fail("Package scanning unexpectedly found the entity class on Android");
    } catch (RuntimeException e) {
      Log.i(TAG, "Package scanning on Android fails as expected: " + e);
    }
  }

  @Test
  public void testCrudWithExplicitEntityClasses() {
    JsonDBTemplate db = templateWithData();
    assertEquals(4, db.findAll(Device.class).size());
    assertEquals("a", db.findById("1", Device.class).getName());

    Device d = db.findById("2", Device.class);
    d.setName("b-renamed");
    db.save(d, Device.class);
    assertEquals("b-renamed", db.findById("2", Device.class).getName());

    Device existing = db.findById("1", Device.class);
    existing.setName("a-upserted");
    db.upsert(Arrays.asList(existing, device("5", "e", 22, true, Device.Status.ACTIVE, "Delhi", null)), Device.class);
    assertEquals("a-upserted", db.findById("1", Device.class).getName());
    assertEquals("e", db.findById("5", Device.class).getName());

    db.remove(db.findById("4", Device.class), Device.class);
    assertNull(db.findById("4", Device.class));
    assertEquals(4, db.findAll(Device.class).size());

    JsonDBTemplate reloaded = new JsonDBTemplate(dbDir.getAbsolutePath(), ENTITIES);
    assertEquals(4, reloaded.findAll(Device.class).size());
    assertEquals("a-upserted", reloaded.findById("1", Device.class).getName());
  }

  @Test
  public void testQueries() {
    JsonDBTemplate db = templateWithData();
    assertEquals(Arrays.asList("1"), ids(db.find("/.[name='a']", Device.class)));
    assertEquals(Arrays.asList("1", "3"), ids(db.find("/.[port>1000]", Device.class)));
    assertEquals(Arrays.asList("1", "3"), ids(db.find("/.[active=true()]", Device.class)));
    assertEquals(Arrays.asList("1", "3", "4"), ids(db.find("/.[status='ACTIVE']", Device.class)));
    assertEquals(Arrays.asList("1", "4"), ids(db.find("/.[location/city='Pune']", Device.class)));
    assertEquals(Arrays.asList("1", "2"), ids(db.find("/.[tags='y']", Device.class)));
    assertEquals(Arrays.asList("1"), ids(db.find("/.[active=true() and location/city='Pune']", Device.class)));
    assertEquals("2", db.findOne("/.[name='b']", Device.class).getId());
  }

  @Test
  public void testQueriesCannotCallJavaMethods() {
    JsonDBTemplate db = templateWithData();
    try {
      db.find("/.[java.lang.System.setProperty('jsondb.android.probe','called')]", Device.class);
    } catch (RuntimeException expected) {
      // Rejecting the query is fine, running it is not
    }
    assertNull(System.getProperty("jsondb.android.probe"));
  }

  @Test
  public void testFindAndModify() {
    JsonDBTemplate db = templateWithData();
    assertEquals(Device.Status.RETIRED, db.findAndModify("/.[id='1']", Update.update("status", "RETIRED"), Device.class).getStatus());
    assertEquals("Delhi", db.findAndModify("/.[id='1']", Update.update("location.city", "Delhi"), Device.class).getLocation().getCity());
    assertEquals(4, db.findAllAndModify("/.", Update.update("port", "1"), Device.class).size());
    for (Device d : db.findAll(Device.class)) {
      assertEquals(1, d.getPort());
    }
    try {
      db.findAndModify("/.[id='2']", Update.update("nosuch", "x"), Device.class);
      fail("Expected an unknown property to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      // Rejecting is the correct outcome
    }
  }

  @Test
  public void testEncryptedSecretField() throws Exception {
    JsonDBTemplate db = new JsonDBTemplate(dbDir.getAbsolutePath(), ENTITIES, new Default1Cipher("1r8+24pibarAWgS85/Heeg=="));
    db.createCollection(Device.class);
    Device d = device("1", "a", 1, true, Device.Status.ACTIVE, null, null);
    d.setToken("a secret");
    db.insert(d);
    assertEquals("a secret", db.findById("1", Device.class).getToken());
  }

  /**
   * Written on the JVM by Default1Cipher when it was pinned to SunJCE; Android's provider must read it
   */
  @Test
  public void testDecryptsGcmCiphertextWrittenOnTheJvm() throws Exception {
    assertEquals("Hallo, Wie gehts", new Default1Cipher("1r8+24pibarAWgS85/Heeg==")
        .decrypt("5o8UGmP/pQEpcmJ7qYPue9/2Oo5bQD7e0dgtudMG1Isj0dbOtRZFGKLDkFEE6mtr"));
  }

  /**
   * The deprecated CBC cipher is deterministic, so Android must produce exactly the JVM's ciphertext
   */
  @Test
  public void testDeprecatedCbcCipherMatchesTheJvm() throws Exception {
    ICipher cbc = new DefaultAESCBCCipher(CryptoUtil.generate128BitKey("MyPassword", "ksdfkja923u4anf"));
    assertEquals("cEIxZFDVSlWIN+ZmOGyvmbWv4+ziI884BNu0Hplglws=", cbc.encrypt("Hallo, Wie gehts"));
    assertEquals("Hallo, Wie gehts", cbc.decrypt("cEIxZFDVSlWIN+ZmOGyvmbWv4+ziI884BNu0Hplglws="));
  }

  @Test
  public void testBackupAndRestore() {
    JsonDBTemplate db = templateWithData();
    File backup = new File(dbDir.getParentFile(), dbDir.getName() + "-backup.zip");
    try {
      db.backup(backup.getAbsolutePath());
      db.remove(db.findById("1", Device.class), Device.class);
      assertEquals(3, db.findAll(Device.class).size());
      db.restore(backup.getAbsolutePath(), false);
      assertEquals(4, db.findAll(Device.class).size());
    } finally {
      backup.delete();
    }
  }
}
