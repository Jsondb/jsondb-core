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
import java.io.File;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Junit tests for the save() apis
 * 
 * @version 1.0 08-Oct-2016
 */
public class SaveTests {
    private String dbFilesLocation = "src/test/resources/dbfiles/saveTests";
    private File dbFilesFolder = new File(dbFilesLocation);
    private File instancesJson = new File(dbFilesFolder, "instances.json");

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
     * Test to save a new object into a non-existing collection.
     */
    @Test
    public void testSave_IntoNonExistingCollection() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.save(new Site(), "sites"));
        assertEquals("Collection by name 'sites' not found. Create collection first.", exception.getMessage());
    }

    private class SomeClass {
    }

    /**
     * Test to save a new object of unknown collection type.
     */
    @Test
    public void testSave_IntoUnknowCollection() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.save(new SomeClass(), SomeClass.class));
        assertEquals("Entity 'SomeClass' is not annotated with annotation @Document", exception.getMessage());
    }

    /**
     * Test to save a null object.
     */
    @Test
    public void testSave_ANullObject() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.save(null, Instance.class));
        assertEquals("Null Object cannot be updated into DB", exception.getMessage());
    }

    /**
     * Test to save a Collection object.
     */
    @Test
    public void testSave_ASingleCollectionObject() {
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.save(new HashSet<Instance>(), Instance.class));
        assertEquals("Collection object cannot be inserted, removed, updated or upserted as a single object", exception.getMessage());
    }

    /**
     * Test to save a new object that does not exists in the collection
     */
    @Test
    public void testSave_ANewObject() {
        Instance instance = new Instance();
        instance.setId("15");
        instance.setHostname("ec2-54-191-15");
        InvalidJsonDbApiUsageException exception = assertThrows(InvalidJsonDbApiUsageException.class, () -> jsonDBTemplate.save(instance, Instance.class));
        assertEquals("Document with Id: '15' not found in Collection by name 'instances' not found. Insert or Upsert the object first.", exception.getMessage());
    }

    /**
     * Test to simply save/update a object
     */
    @Test
    public void testSave_Simple() {
        List<Instance> instances = jsonDBTemplate.getCollection(Instance.class);
        int size = instances.size();

        Instance instance = new Instance();
        instance.setId("01");
        instance.setHostname("ec2-54-191-UPDTed");
        jsonDBTemplate.save(instance, Instance.class);

        Instance instance1 = jsonDBTemplate.findById("01", Instance.class);
        assertEquals("ec2-54-191-UPDTed", instance1.getHostname());
        assertEquals(size, instances.size());
    }
}
