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
import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.crypto.DefaultAESCBCCipher;
import io.jsondb.crypto.ICipher;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;
import io.jsondb.tests.model.Volume;
import io.jsondb.tests.util.TestUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Junit Tests for the insert() apis
 * 
 * @version 1.0 06-Oct-2016
 */
public class InsertTests {

    private String dbFilesLocation = "src/test/resources/dbfiles/insertTests";
    private File dbFilesFolder = new File(dbFilesLocation);
    private File instancesJson = new File(dbFilesFolder, "instances.json");
    private File sitesJson = new File(dbFilesFolder, "sites.json");

    private JsonDBTemplate jsonDBTemplate = null;

    @BeforeEach
    public void setUp() throws Exception {
        dbFilesFolder.mkdir();
        Files.copy(new File("src/test/resources/dbfiles/instances.json"), instancesJson);
        ICipher cipher = new DefaultAESCBCCipher("1r8+24pibarAWgS85/Heeg==");
        jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model", cipher);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Util.delete(dbFilesFolder);
    }

    /**
     * Test to insert a new object into a non-existing collection.
     */
    @Test
    public void testInsert_IntoNonExistingCollection() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(new Site()));
        assertEquals("Collection by name 'sites' not found. Create collection first", exception.getMessage());
    }

    private class SomeClass {
    }

    /**
     * Test to insert a new object of unknown collection type.
     */
    @Test
    public void testInsert_IntoUnknowCollection() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(new SomeClass()));
        assertEquals("Entity 'SomeClass' is not annotated with annotation @Document", exception.getMessage());
    }

    /**
     * Test to insert a null object.
     */
    @Test
    public void testInsert_ANullObject_1() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(null));
        assertEquals("Null Object cannot be inserted into DB", exception.getMessage());
    }

    /**
     * Test to insert a null object.
     */
    @Test
    public void testInsert_ANullObject_2() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(null, "sites"));
        assertEquals("Null Object batch cannot be inserted into DB", exception.getMessage());
    }

    /**
     * Test to insert a null object.
     */
    @Test
    public void testInsert_ANullObject_3() {
        Object nullObject = null;
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(nullObject, "sites"));
        assertEquals("Null Object cannot be inserted into DB", exception.getMessage());
    }

    /**
     * Test to insert a Collection object.
     */
    @Test
    public void testInsert_ASingleCollectionObject() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(new HashSet<String>()));
        assertEquals("Collection object cannot be inserted, removed, updated or upserted as a single object", exception.getMessage());
    }

    /**
     * Test to insert a new object with a ID that already exists..
     */
    @Test
    public void testInsert_AObjectWithIDAlreadyInDb() {
        Instance instance = new Instance();
        instance.setId("01");
        instance.setHostname("ec2-54-191-01");
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(instance));
        assertEquals("Object already present in Collection. Use Update or Upsert operation instead of Insert", exception.getMessage());
    }

    /**
     * Test to insert a new object into a known collection type which has some data.
     */
    @Test
    public void testInsert_NewObject() {
        List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
        int size = instances.size();

        Instance instance = new Instance();
        instance.setId("11");
        instance.setHostname("ec2-54-191-11");
        jsonDBTemplate.insert(instance);

        instances = jsonDBTemplate.getCollection(Instance.class);
        assertNotNull(instances);
        assertEquals(size + 1, instances.size());
    }

    /**
     * Test to insert a new object into a collection and verify the actual file output.
     */
    @Test
    public void testInsert_NewObjectAndVerify() {
        List<Site> sites = jsonDBTemplate.getCollection(Site.class);
        int size = sites.size();

        Site site = new Site();
        site.setId("01");
        site.setLocation("Oregon");
        jsonDBTemplate.insert(site);

        sites = jsonDBTemplate.getCollection(Site.class);
        assertNotNull(sites);
        assertEquals(size + 1, sites.size());

        String[] expectedLinesAtEnd = { "{\"id\":\"01\",\"location\":\"Oregon\"}" };

        TestUtils.checkLastLines(sitesJson, expectedLinesAtEnd);
    }

    /**
     * Test to insert a collection of objects.
     */
    @Test
    public void testInsert_ACollectionOfObjects() {
        List<Site> sites = jsonDBTemplate.getCollection(Site.class);
        int size = sites.size();

        List<Site> newList = new ArrayList<Site>();
        for (int i = 0; i < 5; i++) {
            Site site = new Site();
            int id = 2 + i;
            site.setId(String.format("%02d", id));
            site.setLocation("Singapore");
            newList.add(site);
        }
        jsonDBTemplate.insert(newList, Site.class);

        sites = jsonDBTemplate.getCollection(Site.class);
        assertNotNull(sites);
        assertEquals(size + 5, sites.size());

        String[] expectedLinesAtEnd = {
                "{\"schemaVersion\":\"1.0\"}",
                "{\"id\":\"02\",\"location\":\"Singapore\"}",
                "{\"id\":\"03\",\"location\":\"Singapore\"}",
                "{\"id\":\"04\",\"location\":\"Singapore\"}",
                "{\"id\":\"05\",\"location\":\"Singapore\"}",
                "{\"id\":\"06\",\"location\":\"Singapore\"}" };

        TestUtils.checkLastLines(sitesJson, expectedLinesAtEnd);
    }

    /**
     * Test to insert a collection of objects with one of them already present in DB.
     */
    @Test
    public void testInsert_ACollectionOfObjectsWithOneAlreadyPresent() {
        List<Instance> newList = new ArrayList<Instance>();
        for (int i = 0; i < 5; i++) {
            Instance c = new Instance();
            int id = 21 + i;
            c.setId(String.format("%02d", id));
            newList.add(c);
        }
        Instance instance = new Instance();
        instance.setId("03");
        newList.add(instance);

        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(newList, Instance.class));
        assertEquals("Object already present in Collection. Use Update or Upsert operation instead of Insert", exception.getMessage());
    }

    /**
     * Test to insert a collection of objects with one of them already present in DB.
     */
    @Test
    public void testInsert_DuplicateObjectWithinObjectsBeingInserted() {
        List<Instance> newList = new ArrayList<Instance>();
        for (int i = 0; i < 2; i++) {
            Instance c = new Instance();
            c.setId("28");
            newList.add(c);
        }

        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(newList, Instance.class));
        assertEquals("Duplicate object with id: 28 within the passed in parameter", exception.getMessage());
    }

    /**
     * Test to insert a collection of objects when db collection does not exists.
     */
    @Test
    public void testInsert_CollectionOfObjectsWhenDBCollectionDoesNotExist() {
        List<Volume> newList = new ArrayList<Volume>();
        for (int i = 0; i < 5; i++) {
            Volume c = new Volume();
            int id = 0 + i;
            c.setId(String.format("%06d", id));
            newList.add(c);
        }

        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.insert(newList, Volume.class));
        assertEquals("Collection by name 'volumes' not found. Create collection first", exception.getMessage());
    }

    /**
     * Test to insert a objects without a ID set, the inserted Object will have a generated ID.
     */
    @Test
    public void testInsert_WithoutIdSet() {
        List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
        int size = instances.size();

        Instance instance = new Instance();
        instance.setHostname("ec2-54-191-11");
        // Private key is encrypted form of: b87eb02f5dd7e5232d7b0fc30a5015e4
        // Inserting the below private key should result in "Zf9vl5K6WV6BA3eL7JbnrfPMjfJxc9Rkoo0zlROQlgTslmcp9iFzos+MP93GZqop"
        instance.setPrivateKey("b87eb02f5dd7e5232d7b0fc30a5015e4");
        instance.setPublicKey("d3aa045f71bf4d1dffd2c5f485a4bc1d");

        jsonDBTemplate.insert(instance);

        instances = jsonDBTemplate.getCollection(Instance.class);
        assertNotNull(instances);
        assertEquals(size + 1, instances.size());
        for (Instance instance1 : instances) {
            assertNotNull(instance1.getId());
        }
    }
}
