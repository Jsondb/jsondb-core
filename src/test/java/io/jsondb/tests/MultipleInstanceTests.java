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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.noid.DocumentWithoutId;

/**
 * Two JsonDBTemplate instances on the same folder in one JVM lose each other's writes (issue #51).
 * For now JsonDB warns when that happens; close() releases a folder.
 */
public class MultipleInstanceTests {
  private static final String WARNING = "is already open by another JsonDBTemplate";

  private File dbFilesFolder;
  private File otherFolder;

  @Before
  public void setUp() throws Exception {
    // Folder names unique to each test, because which folders are open is JVM-wide state
    String unique = Long.toString(System.nanoTime());
    dbFilesFolder = new File("src/test/resources/dbfiles/multipleInstanceTests-" + unique);
    otherFolder = new File("src/test/resources/dbfiles/multipleInstanceTests-other-" + unique);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
    Util.delete(otherFolder);
  }

  private JsonDBTemplate open(File folder) {
    return new JsonDBTemplate(folder.getPath(), "io.jsondb.tests.model");
  }

  private interface Action {
    void run() throws Exception;
  }

  /**
   * Runs the action and returns what was logged to System.err while it ran
   */
  private String loggedDuring(Action action) throws Exception {
    PrintStream original = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, "UTF-8"));
    try {
      action.run();
    } finally {
      System.setErr(original);
    }
    return captured.toString("UTF-8");
  }

  @Test
  public void testSecondInstanceOnSameFolderWarns() throws Exception {
    JsonDBTemplate first = open(dbFilesFolder);
    try {
      String logged = loggedDuring(() -> open(dbFilesFolder));
      assertTrue("expected a warning, logged: " + logged, logged.contains(WARNING));
      assertTrue("warning should name the folder, logged: " + logged, logged.contains(dbFilesFolder.getName()));
    } finally {
      first.close();
    }
  }

  @Test
  public void testFirstInstanceDoesNotWarn() throws Exception {
    String logged = loggedDuring(() -> open(dbFilesFolder).close());
    assertFalse("unexpected warning: " + logged, logged.contains(WARNING));
  }

  @Test
  public void testInstancesOnDifferentFoldersDoNotWarn() throws Exception {
    JsonDBTemplate first = open(dbFilesFolder);
    try {
      String logged = loggedDuring(() -> open(otherFolder).close());
      assertFalse("unexpected warning: " + logged, logged.contains(WARNING));
    } finally {
      first.close();
    }
  }

  @Test
  public void testClosedInstanceReleasesTheFolder() throws Exception {
    open(dbFilesFolder).close();
    String logged = loggedDuring(() -> open(dbFilesFolder).close());
    assertFalse("unexpected warning after close(): " + logged, logged.contains(WARNING));
  }

  @Test
  public void testTryWithResourcesReleasesTheFolder() throws Exception {
    try (JsonDBTemplate db = open(dbFilesFolder)) {
      assertTrue(db.getCollectionNames().isEmpty());
    }
    String logged = loggedDuring(() -> open(dbFilesFolder).close());
    assertFalse("unexpected warning after try-with-resources: " + logged, logged.contains(WARNING));
  }

  /**
   * Closing twice must not release a folder that another instance still has open
   */
  @Test
  public void testCloseIsIdempotent() throws Exception {
    JsonDBTemplate first = open(dbFilesFolder);
    first.close();
    first.close();
    JsonDBTemplate second = open(dbFilesFolder);
    try {
      String logged = loggedDuring(() -> open(dbFilesFolder).close());
      assertTrue("expected a warning, logged: " + logged, logged.contains(WARNING));
    } finally {
      second.close();
    }
  }

  /**
   * A constructor that fails must not leave the folder counted as open
   */
  @Test
  public void testFailedConstructionDoesNotKeepTheFolderOpen() throws Exception {
    try {
      new JsonDBTemplate(dbFilesFolder.getPath(), Collections.<Class<?>>singletonList(DocumentWithoutId.class));
      fail("Expected the class without @Id to be rejected");
    } catch (InvalidJsonDbApiUsageException expected) {
      // Rejecting is the correct outcome
    }
    String logged = loggedDuring(() -> open(dbFilesFolder).close());
    assertFalse("unexpected warning after a failed construction: " + logged, logged.contains(WARNING));
  }

  /**
   * A folder reached through a symbolic link is the same folder, also when the first instance creates it
   */
  @Test
  public void testSameFolderThroughASymbolicLinkWarns() throws Exception {
    assertTrue(otherFolder.mkdirs());
    Path link = dbFilesFolder.toPath();
    try {
      Files.createSymbolicLink(link, otherFolder.getAbsoluteFile().toPath());
    } catch (UnsupportedOperationException | IOException e) {
      assumeNoException("cannot create symbolic links here", e);
    }
    File db = new File(dbFilesFolder, "db");
    JsonDBTemplate first = open(db);
    try {
      String logged = loggedDuring(() -> open(new File(otherFolder, "db")).close());
      assertTrue("expected a warning, logged: " + logged, logged.contains(WARNING));
    } finally {
      first.close();
    }
  }
}
