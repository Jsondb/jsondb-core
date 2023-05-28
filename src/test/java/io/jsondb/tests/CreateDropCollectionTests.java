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
import io.jsondb.DefaultSchemaVersionComparator;
import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Farooq Khan
 * @version 1.0 06-Oct-2016
 */
public class CreateDropCollectionTests {
    private String dbFilesLocation = "src/test/resources/dbfiles/createDropCollectionTests";
    private File dbFilesFolder = new File(dbFilesLocation);
    private File instancesJson = new File(dbFilesFolder, "instances.json");
    private File sitesJson = new File(dbFilesFolder, "sites.json");

    private JsonDBTemplate jsonDBTemplate = null;

    @BeforeEach
    public void setUp() throws Exception {
        dbFilesFolder.mkdir();
        Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
        jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", false, new DefaultSchemaVersionComparator());
    }

    @AfterEach
    public void tearDown() throws Exception {
        Util.delete(dbFilesFolder);
    }

    @Test
    public void testCreateDropCollection() {
        Set<String> collectionNames = jsonDBTemplate.getCollectionNames();
        assertTrue(collectionNames.contains("instances"));
        assertEquals("instances", jsonDBTemplate.getCollectionName(Instance.class));
        assertEquals(collectionNames.size(), 1);

        jsonDBTemplate.createCollection(Site.class);
        assertTrue(sitesJson.exists());

        collectionNames = jsonDBTemplate.getCollectionNames();
        assertTrue(collectionNames.contains("instances"));
        assertTrue(collectionNames.contains("sites"));
        assertEquals(collectionNames.size(), 2);

        List<Site> sites = jsonDBTemplate.findAll(Site.class);
        assertEquals(sites.size(), 0);

        jsonDBTemplate.dropCollection(Site.class);
        assertFalse(sitesJson.exists());

        collectionNames = jsonDBTemplate.getCollectionNames();
        assertTrue(collectionNames.contains("instances"));
        assertEquals(collectionNames.size(), 1);
    }

    @Test
    public void testGetCollection() {
        Set<String> collectionNames = jsonDBTemplate.getCollectionNames();
        assertTrue(collectionNames.contains("instances"));
        assertEquals(collectionNames.size(), 1);

        jsonDBTemplate.getCollection(Site.class);
        assertTrue(sitesJson.exists());

        collectionNames = jsonDBTemplate.getCollectionNames();
        assertTrue(collectionNames.contains("instances"));
        assertTrue(collectionNames.contains("sites"));
        assertEquals(collectionNames.size(), 2);

        jsonDBTemplate.dropCollection(Site.class);
        assertFalse(sitesJson.exists());
    }

    @Test
    public void testCreateCollectionForUnknownCollectionName() {
        assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.createCollection("somecollection"));
    }

    private class SomeClass {
    }

    @Test
    public void testCreateCollectionForUnknownCollectionClass() {
        assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.createCollection(SomeClass.class));
    }

    @Test
    public void testCreateCollectionWhenAlreadyExisting() {
        assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.createCollection("instances"));
    }

    @Test
    public void testCollectionExists() {
        assertFalse(jsonDBTemplate.collectionExists("somecollection"));
    }

    @Test
    public void testDropCollectionForUnknownCollection() {
        assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.dropCollection("SomeCollection"));
    }
}
