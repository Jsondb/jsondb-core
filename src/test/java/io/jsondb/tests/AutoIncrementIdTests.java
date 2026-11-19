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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.jsondb.InvalidJsonDbApiUsageException;
import io.jsondb.JsonDBTemplate;
import io.jsondb.Util;
import io.jsondb.tests.model.Invoice;
import io.jsondb.tests.model.Ticket;

/**
 * Documents with a numeric @Id saved without one get the next number after the highest id in the
 * collection (#36)
 */
public class AutoIncrementIdTests {
  private String dbFilesLocation = "src/test/resources/dbfiles/autoIncrementIdTests";
  private File dbFilesFolder = new File(dbFilesLocation);

  private JsonDBTemplate jsonDBTemplate = null;

  @Before
  public void setUp() {
    dbFilesFolder.mkdir();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    jsonDBTemplate.createCollection(Ticket.class);
    jsonDBTemplate.createCollection(Invoice.class);
  }

  @After
  public void tearDown() {
    jsonDBTemplate.close();
    Util.delete(dbFilesFolder);
  }

  private Set<Long> ticketIds() {
    Set<Long> ids = new TreeSet<Long>();
    for (Ticket t : jsonDBTemplate.findAll(Ticket.class)) {
      ids.add(t.getId());
    }
    return ids;
  }

  @Test
  public void testLongIdsCountUp() {
    jsonDBTemplate.insert(new Ticket(null, "first"));
    jsonDBTemplate.insert(new Ticket(null, "second"));
    assertEquals("first", jsonDBTemplate.findById(1L, Ticket.class).getTitle());
    assertEquals("second", jsonDBTemplate.findById(2L, Ticket.class).getTitle());
  }

  @Test
  public void testNextIdFollowsTheHighestExistingId() {
    jsonDBTemplate.insert(new Ticket(10L, "ten"));
    jsonDBTemplate.insert(new Ticket(null, "next"));
    assertEquals("next", jsonDBTemplate.findById(11L, Ticket.class).getTitle());
  }

  @Test
  public void testPrimitiveIntZeroMeansNoId() {
    jsonDBTemplate.insert(new Invoice(0, "a"));
    jsonDBTemplate.insert(new Invoice(0, "b"));
    assertEquals("a", jsonDBTemplate.findById(1, Invoice.class).getCustomer());
    assertEquals("b", jsonDBTemplate.findById(2, Invoice.class).getCustomer());
  }

  @Test
  public void testBatchInsertNumbersInOrder() {
    jsonDBTemplate.insert(Arrays.asList(new Ticket(null, "a"), new Ticket(null, "b"), new Ticket(null, "c")), Ticket.class);
    assertEquals("a", jsonDBTemplate.findById(1L, Ticket.class).getTitle());
    assertEquals("c", jsonDBTemplate.findById(3L, Ticket.class).getTitle());
  }

  /**
   * An explicit id later in the same batch is not handed out to an earlier document without one
   */
  @Test
  public void testBatchSkipsIdsTakenLaterInTheBatch() {
    jsonDBTemplate.insert(Arrays.asList(new Ticket(null, "generated"), new Ticket(1L, "explicit")), Ticket.class);
    assertEquals("explicit", jsonDBTemplate.findById(1L, Ticket.class).getTitle());
    assertEquals("generated", jsonDBTemplate.findById(2L, Ticket.class).getTitle());
  }

  @Test
  public void testUpsertWithoutIdInserts() {
    jsonDBTemplate.insert(new Ticket(null, "first"));
    jsonDBTemplate.upsert(new Ticket(null, "second"));
    jsonDBTemplate.upsert(Arrays.asList(new Ticket(null, "third"), new Ticket(1L, "first, changed")), Ticket.class);
    assertEquals("first, changed", jsonDBTemplate.findById(1L, Ticket.class).getTitle());
    assertEquals("second", jsonDBTemplate.findById(2L, Ticket.class).getTitle());
    assertEquals("third", jsonDBTemplate.findById(3L, Ticket.class).getTitle());
  }

  @Test
  public void testNumberingContinuesAfterReopening() {
    jsonDBTemplate.insert(new Ticket(null, "first"));
    jsonDBTemplate.insert(new Ticket(null, "second"));
    jsonDBTemplate.close();
    jsonDBTemplate = new JsonDBTemplate(dbFilesLocation, "io.jsondb.tests.model");
    jsonDBTemplate.insert(new Ticket(null, "third"));
    assertEquals("third", jsonDBTemplate.findById(3L, Ticket.class).getTitle());
  }

  /**
   * Documented behaviour: ids are not stored separately, so deleting the highest one frees it again
   */
  @Test
  public void testDeletedHighestIdIsReused() {
    jsonDBTemplate.insert(Arrays.asList(new Ticket(null, "a"), new Ticket(null, "b"), new Ticket(null, "c")), Ticket.class);
    jsonDBTemplate.remove(jsonDBTemplate.findById(3L, Ticket.class), Ticket.class);
    jsonDBTemplate.insert(new Ticket(null, "d"));
    assertEquals("d", jsonDBTemplate.findById(3L, Ticket.class).getTitle());
  }

  @Test
  public void testConcurrentInsertsGetDistinctIds() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Future<?>> futures = new ArrayList<Future<?>>();
    for (int t = 0; t < 8; t++) {
      futures.add(pool.submit(() -> {
        for (int i = 0; i < 25; i++) {
          jsonDBTemplate.insert(new Ticket(null, "x"));
        }
      }));
    }
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();
    Set<Long> expected = new TreeSet<Long>();
    for (long i = 1; i <= 200; i++) {
      expected.add(i);
    }
    assertEquals(expected, ticketIds());
  }

  @Test
  public void testIdTypeOverflowIsRejected() {
    jsonDBTemplate.insert(new Invoice(Integer.MAX_VALUE, "last"));
    try {
      jsonDBTemplate.insert(new Invoice(0, "one too many"));
      fail("an id beyond Integer.MAX_VALUE was handed out");
    } catch (InvalidJsonDbApiUsageException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("no id left"));
    }
    assertEquals(1, jsonDBTemplate.findAll(Invoice.class).size());
  }
}
