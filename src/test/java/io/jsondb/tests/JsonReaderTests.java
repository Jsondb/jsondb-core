/*
 * Copyright (c) 2017 Farooq Khan
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
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Files;

import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.JsonDBConfig;
import io.jsondb.Util;
import io.jsondb.io.JsonFileLockException;
import io.jsondb.io.JsonReader;

/**
 * Unit tests for JsonReader IO utility class
 * @version 1.0 11-Dec-2017
 */
public class JsonReaderTests {
  
  private String dbFilesLocation = "src/test/resources/dbfiles/jsonReaderTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");
  
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  
  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    dbFilesFolder.mkdir();
    Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
  }

  @After
  public void tearDown() throws Exception {
    Util.delete(dbFilesFolder);
  }

  @Test
  public void testReadAllLines() throws IOException {
    JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false,
        new DefaultSchemaVersionComparator());

    JsonReader jr = new JsonReader(dbConfig, instancesJson);
    int lineCount = 0;
    String line;
    while ((line = jr.readLine()) != null) {
      assertNotNull(line);
      lineCount++;
    }
    jr.close();
    assertEquals(7, lineCount);
  }

  @Test
  public void testReadLine() throws IOException {
    JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false,
        new DefaultSchemaVersionComparator());
    
    JsonReader jr = new JsonReader(dbConfig, instancesJson);
    
    assertNotNull(jr);
    assertEquals("{\"schemaVersion\":\"1.0\"}", jr.readLine());
  }
  
  @Test
  public void testReadLineAndClose() throws IOException {
    JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false,
        new DefaultSchemaVersionComparator());

    JsonReader jr = new JsonReader(dbConfig, instancesJson);
    assertEquals("{\"schemaVersion\":\"1.0\"}", jr.readLine());
    assertNotNull(jr.readLine());
    jr.close();
  }

  @Test
  public void testLockException() throws IOException {
    File lockFolder = new File(dbFilesLocation, "lock");
    if (!lockFolder.exists()) {
      lockFolder.mkdirs();
    }
    File fileLockLocation = new File(lockFolder, "instances.json.lock");
    RandomAccessFile raf = new RandomAccessFile(fileLockLocation, "rw");
    raf.writeInt(0); //Will cause creation of the file
    
    FileChannel channel = raf.getChannel();
    try {
      channel.lock();
    } catch (IOException e) {
      //Ignore
    }
    
    expectedException.expect(JsonFileLockException.class);
    expectedException.expectMessage("JsonReader failed to obtain a file lock for file " + fileLockLocation);
    
    JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false,
        new DefaultSchemaVersionComparator());
    
    @SuppressWarnings("unused")
    JsonReader jr = new JsonReader(dbConfig, instancesJson);
    raf.close();
  }
}
