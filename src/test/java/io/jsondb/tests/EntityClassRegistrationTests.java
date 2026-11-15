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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

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
import io.jsondb.crypto.ICipher;
import io.jsondb.tests.duplicates.DuplicateInstance;
import io.jsondb.tests.model.Instance;
import io.jsondb.tests.model.Site;
import io.jsondb.tests.model.Volume;

/**
 * Entity classes can be handed to JsonDB directly instead of being found by package scanning,
 * which does not work on Android or under OSGi (issues #28, #42, #53).
 */
public class EntityClassRegistrationTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/entityClassRegistrationTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File instancesJson = new File(dbFilesFolder, "instances.json");

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

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
  public void testRegisteredClassesAreLoaded() {
    Collection<Class<?>> entities = Arrays.<Class<?>>asList(Instance.class, Site.class);
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, entities);

    Set<String> collectionNames = jsonDBTemplate.getCollectionNames();
    assertEquals(1, collectionNames.size());
    assertTrue(collectionNames.contains("instances"));
    assertEquals(6, jsonDBTemplate.findAll(Instance.class).size());

    jsonDBTemplate.createCollection(Site.class);
    assertTrue(jsonDBTemplate.collectionExists(Site.class));
  }

  /**
   * Volume is in the same package as the registered classes, so it would be found by scanning.
   * It must not be known here, proving no package scan happened.
   */
  @Test
  public void testUnregisteredClassIsUnknownEvenInSamePackage() {
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, Collections.<Class<?>>singletonList(Instance.class));
    assertFalse(jsonDBTemplate.collectionExists(Volume.class));

    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("No class found with @Document Annotation and attribute collectionName as: volumes");
    jsonDBTemplate.createCollection(Volume.class);
  }

  @Test
  public void testRegisteredClassesWorkWithEncryption() throws Exception {
    ICipher cipher = new Default1Cipher("1r8+24pibarAWgS85/Heeg==");
    JsonDBTemplate jsonDBTemplate = new JsonDBTemplate(dbFilesLocation,
        Collections.<Class<?>>singletonList(Instance.class), cipher);

    Instance instance = new Instance();
    instance.setId("07");
    instance.setHostname("ec2-54-191-07");
    instance.setPrivateKey("a secret");
    jsonDBTemplate.insert(instance);

    assertEquals("a secret", jsonDBTemplate.findById("07", Instance.class).getPrivateKey());
  }

  @Test
  public void testNullEntityClassesIsRejected() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Entity classes cannot be null");
    Collection<Class<?>> entities = null;
    new JsonDBTemplate(dbFilesLocation, entities);
  }

  @Test
  public void testClassWithoutDocumentAnnotationIsRejected() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Entity 'String' is not annotated with annotation @Document");
    new JsonDBTemplate(dbFilesLocation, Arrays.<Class<?>>asList(Instance.class, String.class));
  }

  @Test
  public void testTwoClassesWithSameCollectionNameAreRejected() {
    expectedException.expect(InvalidJsonDbApiUsageException.class);
    expectedException.expectMessage("Collection name 'instances' is used by both");
    new JsonDBTemplate(dbFilesLocation, Arrays.<Class<?>>asList(Instance.class, DuplicateInstance.class));
  }
}
