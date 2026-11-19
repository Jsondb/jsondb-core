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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.IdGenerator;
import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Invoice;
import io.jsondb.tests.model.Site;
import io.jsondb.tests.model.Ticket;

/**
 * Ids for documents saved without one can come from an IdGenerator set for the collection's class or
 * as the default
 */
public class IdGeneratorTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/idGeneratorTests";
  private File dbFilesFolder = new File(dbFilesLocation);
  private File sitesJson = new File(dbFilesFolder, "sites.json");

  private JsonDBConfig config;
  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() {
    dbFilesFolder.mkdir();
    config = new JsonDBConfig(dbFilesLocation, "io.jsondb.tests.model", null, false, null);
    jsonDBTemplate = new JsonDBTemplate(config);
    jsonDBTemplate.createCollection(Site.class);
    jsonDBTemplate.createCollection(Ticket.class);
    jsonDBTemplate.createCollection(Invoice.class);
  }

  @After
  public void tearDown() {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private static Site site(String id, String location) {
    Site s = new Site();
    s.setId(id);
    s.setLocation(location);
    return s;
  }

  private static IdGenerator counting(String prefix) {
    AtomicInteger n = new AtomicInteger();
    return (collectionName, document) -> prefix + n.incrementAndGet();
  }

  @Test
  public void testDefaultGenerator() {
    config.setIdGenerator(counting("S-"));
    jsonDBTemplate.insert(site(null, "a"));
    jsonDBTemplate.insert(Arrays.asList(site(null, "b"), site(null, "c")), Site.class);
    assertEquals("a", jsonDBTemplate.findById("S-1", Site.class).getLocation());
    assertEquals("c", jsonDBTemplate.findById("S-3", Site.class).getLocation());
  }

  @Test
  public void testClassGeneratorWinsOverDefault() {
    config.setIdGenerator(counting("default-"));
    config.setIdGenerator(Site.class, counting("site-"));
    jsonDBTemplate.insert(site(null, "a"));
    assertEquals("a", jsonDBTemplate.findById("site-1", Site.class).getLocation());
  }

  @Test
  public void testGeneratorReplacesAutoIncrement() {
    jsonDBTemplate.setIdGenerator(Ticket.class, (collectionName, document) -> 1000L);
    jsonDBTemplate.insert(new Ticket(null, "t"));
    assertEquals("t", jsonDBTemplate.findById(1000L, Ticket.class).getTitle());
  }

  @Test
  public void testGeneratorForPrimitiveId() {
    jsonDBTemplate.setIdGenerator(Invoice.class, (collectionName, document) -> 7);
    jsonDBTemplate.insert(new Invoice(0, "c"));
    assertEquals("c", jsonDBTemplate.findById(7, Invoice.class).getCustomer());
  }

  @Test
  public void testGeneratorSeesCollectionAndDocument() {
    List<String> seen = new ArrayList<String>();
    config.setIdGenerator(Site.class, (collectionName, document) -> {
      seen.add(collectionName + ":" + ((Site) document).getLocation());
      return "x";
    });
    jsonDBTemplate.upsert(site(null, "here"));
    assertEquals(Arrays.asList("sites:here"), seen);
  }

  @Test
  public void testExplicitIdIsKept() {
    config.setIdGenerator(Site.class, (collectionName, document) -> {
      throw new AssertionError("called for a document that has an id");
    });
    jsonDBTemplate.insert(site("mine", "a"));
    assertNotNull(jsonDBTemplate.findById("mine", Site.class));
  }

  @Test
  public void testRemovingTheGeneratorRestoresTheBuiltIn() {
    jsonDBTemplate.setIdGenerator(Ticket.class, (collectionName, document) -> 1000L);
    jsonDBTemplate.setIdGenerator(Ticket.class, null);
    jsonDBTemplate.insert(new Ticket(null, "t"));
    assertEquals("t", jsonDBTemplate.findById(1L, Ticket.class).getTitle());
  }

  @Test
  public void testGetters() {
    IdGenerator generator = counting("g-");
    config.setIdGenerator(generator);
    assertSame(generator, config.getIdGenerator(Ticket.class));
  }

  private void assertRejected(Runnable save, String messagePart) throws Exception {
    jsonDBTemplate.insert(site("existing", "e"));
    byte[] before = Files.readAllBytes(sitesJson.toPath());
    try {
      save.run();
      fail("an id the generator got wrong was accepted");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
    }
    assertEquals(1, jsonDBTemplate.findAll(Site.class).size());
    assertArrayEquals(before, Files.readAllBytes(sitesJson.toPath()));
  }

  @Test
  public void testNullIdIsRejected() throws Exception {
    config.setIdGenerator(Site.class, (collectionName, document) -> null);
    assertRejected(() -> jsonDBTemplate.insert(site(null, "a")), "returned null");
  }

  @Test
  public void testWrongTypeIsRejected() throws Exception {
    config.setIdGenerator(Site.class, (collectionName, document) -> 42L);
    assertRejected(() -> jsonDBTemplate.insert(site(null, "a")), "not a String");
  }

  @Test
  public void testExistingIdIsRejectedOnInsert() throws Exception {
    config.setIdGenerator(Site.class, (collectionName, document) -> "existing");
    assertRejected(() -> jsonDBTemplate.insert(site(null, "a")), "already in collection");
  }

  @Test
  public void testExistingIdIsRejectedOnUpsert() throws Exception {
    config.setIdGenerator(Site.class, (collectionName, document) -> "existing");
    assertRejected(() -> jsonDBTemplate.upsert(site(null, "a")), "already in collection");
  }

  @Test
  public void testExistingIdIsRejectedInBatch() throws Exception {
    config.setIdGenerator(Site.class, (collectionName, document) -> "existing");
    assertRejected(() -> jsonDBTemplate.upsert(Arrays.asList(site(null, "a")), Site.class), "already in collection");
  }

  @Test
  public void testSameIdTwiceInBatchIsRejected() throws Exception {
    config.setIdGenerator(Site.class, (collectionName, document) -> "same");
    assertRejected(() -> jsonDBTemplate.insert(Arrays.asList(site(null, "a"), site(null, "b")), Site.class), "Duplicate object with id: same");
  }
}
