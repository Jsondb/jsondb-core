/*
 * Copyright (c) 2016 Farooq Khan
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
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.events.CollectionFileChangeAdapter;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.PojoWithEnumFields;
import io.jsondb.tests.util.TestUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @version 1.0 24-Oct-2016
 */
public class FileChangeAdapterTests {

    private static final long DB_RELOAD_TIMEOUT = 5 * 1000;
    private String dbFilesLocation = "src/test/resources/dbfiles/changeAdapterTests";
    private File dbFilesFolder = new File(dbFilesLocation);
    private File instancesJson = new File(dbFilesFolder, "instances.json");
    private File pojoWithEnumFieldsJson = new File(dbFilesFolder, "pojowithenumfields.json");

    private JsonDBTemplate jsonDBTemplate = null;

    @BeforeEach
    public void setUp() throws Exception {
        // Filewatcher does not work on Mac and hence JsonDB events will never fire
        // and so the EventTests will never succeed. So we run the tests only if
        // it is not a Mac system
        assumeTrue(!TestUtils.isMac());

        dbFilesFolder.mkdir();
        Files.copy(new File("src/test/resources/dbfiles/pojowithenumfields.json"), pojoWithEnumFieldsJson);
        ICipher cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");

        jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Util.delete(dbFilesFolder);
    }

    private boolean collectionFileAddedFired = false;

    private class FileAddedChangeAdapter extends CollectionFileChangeAdapter {
        @Override
        public void collectionFileAdded(String collectionName) {
            super.collectionFileAdded(collectionName);
            jsonDBTemplate.reloadCollection(collectionName);
            collectionFileAddedFired = true;
        }
    }

    @Test
    public void testAutoReloadOnCollectionFileAdded() {
        jsonDBTemplate.addCollectionFileChangeListener(new FileAddedChangeAdapter());
        assertFalse(jsonDBTemplate.collectionExists(Instance.class));
        try {
            Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
        } catch (IOException e1) {
            fail("Failed to copy data store files");
        }
        try {
            // Give it some time to reload DB
            Thread.sleep(DB_RELOAD_TIMEOUT);
        } catch (InterruptedException e) {
            fail("Failed to wait for db reload");
        }
        assertTrue(collectionFileAddedFired);
        List<Instance> instances = jsonDBTemplate.findAll(Instance.class);
        assertNotNull(instances);
        assertNotEquals(instances.size(), 0);
    }

    private boolean collectionFileModifiedFired = false;

    private class FileModifiedChangeAdapter extends CollectionFileChangeAdapter {
        @Override
        public void collectionFileModified(String collectionName) {
            super.collectionFileModified(collectionName);
            jsonDBTemplate.reloadCollection(collectionName);
            collectionFileModifiedFired = true;
        }
    }

    @Test
    public void testAutoReloadOnCollectionFileModified() throws FileNotFoundException {
        try {
            Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
        } catch (IOException e1) {
            fail("Failed to copy data store files");
        }
        jsonDBTemplate.reLoadDB();
        int oldCount = jsonDBTemplate.findAll(Instance.class).size();
        jsonDBTemplate.addCollectionFileChangeListener(new FileModifiedChangeAdapter());

        @SuppressWarnings("resource")
        Scanner sc = new Scanner(new File("src/test/resources/dbfiles/instances.json")).useDelimiter("\\Z");
        String content = sc.next();
        sc.close();

        content = content + "\n" + "{\"id\":\"07\",\"hostname\":\"ec2-54-191-07\","
                + "\"privateKey\":\"Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop\","
                + "\"publicKey\":\"d3aa045f71bf4d1dffd2c5f485a4bc1d\"}";

        PrintWriter out = new PrintWriter(instancesJson);
        out.println(content);
        out.close();

        try {
            // Give it some time to reload DB
            Thread.sleep(DB_RELOAD_TIMEOUT);
        } catch (InterruptedException e) {
            fail("Failed to wait for db reload");
        }
        assertTrue(collectionFileModifiedFired);
        int newCount = jsonDBTemplate.findAll(Instance.class).size();
        assertEquals(oldCount + 1, newCount);
    }

    private boolean collectionFileDeletedFired = false;

    private class FileDeletedChangeAdapter extends CollectionFileChangeAdapter {
        @Override
        public void collectionFileDeleted(String collectionName) {
            super.collectionFileDeleted(collectionName);
            jsonDBTemplate.reLoadDB();
            collectionFileDeletedFired = true;
        }
    }

    @Test
    public void testAutoReloadOnCollectionFileDeleted() throws FileNotFoundException {
        assertTrue(jsonDBTemplate.collectionExists(PojoWithEnumFields.class));

        jsonDBTemplate.addCollectionFileChangeListener(new FileDeletedChangeAdapter());

        pojoWithEnumFieldsJson.delete();

        try {
            // Give it some time to reload DB
            Thread.sleep(DB_RELOAD_TIMEOUT);
        } catch (InterruptedException e) {
            fail("Failed to wait for db reload");
        }

        assertTrue(collectionFileDeletedFired);
        assertFalse(jsonDBTemplate.collectionExists(PojoWithEnumFields.class));
    }

    private class DoNothingChangeAdapter extends CollectionFileChangeAdapter {
    }

    @Test
    public void testRemoveListener() {
        assertFalse(jsonDBTemplate.hasCollectionFileChangeListener());

        CollectionFileChangeAdapter adapter = new DoNothingChangeAdapter();
        jsonDBTemplate.addCollectionFileChangeListener(adapter);
        assertTrue(jsonDBTemplate.hasCollectionFileChangeListener());

        jsonDBTemplate.removeCollectionFileChangeListener(adapter);
        assertFalse(jsonDBTemplate.hasCollectionFileChangeListener());
    }
}
