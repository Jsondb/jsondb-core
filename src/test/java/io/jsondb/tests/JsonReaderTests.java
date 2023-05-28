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

import com.google.common.io.Files;
import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.JsonDBConfig;
import io.jsondb.Util;
import io.jsondb.io.JsonFileLockException;
import io.jsondb.io.JsonReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for JsonReader IO utility class
 * 
 * @version 1.0 11-Dec-2017
 */
public class JsonReaderTests {

    private String dbFilesLocation = "src/test/resources/dbfiles/jsonReaderTests";
    private File dbFilesFolder = new File(dbFilesLocation);
    private File instancesJson = new File(dbFilesFolder, "instances.json");

    /**
     * @throws java.lang.Exception
     */
    @BeforeEach
    public void setUp() throws Exception {
        dbFilesFolder.mkdir();
        Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Util.delete(dbFilesFolder);
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
    public void testLockException() throws IOException {
        File lockFolder = new File(dbFilesLocation, "lock");
        if (!lockFolder.exists()) {
            lockFolder.mkdirs();
        }
        File fileLockLocation = new File(lockFolder, "instances.json.lock");
        RandomAccessFile raf = new RandomAccessFile(fileLockLocation, "rw");
        raf.writeInt(0); // Will cause creation of the file

        FileChannel channel = raf.getChannel();
        try {
            channel.lock();
        } catch (IOException e) {
            // Ignore
        }

        JsonDBConfig dbConfig = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false, new DefaultSchemaVersionComparator());

        JsonFileLockException exception = assertThrows(JsonFileLockException.class, () -> new JsonReader(dbConfig, instancesJson));
        assertEquals("JsonReader failed to obtain a file lock for file " + fileLockLocation, exception.getMessage());
        raf.close();
    }
}
