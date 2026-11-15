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

import org.junit.Test;

import io.jsondb.query.ddl.AddOperation;
import io.jsondb.query.ddl.CollectionSchemaUpdate;
import io.jsondb.query.ddl.DeleteOperation;
import io.jsondb.query.ddl.RenameOperation;

public class CollectionSchemaUpdateUnitTests {

  @Test
  public void testGetAddOperationsFiltersNullDefaults() {
    CollectionSchemaUpdate update = new CollectionSchemaUpdate()
        .set("ignored", new AddOperation(null, false))
        .set("mac", new AddOperation("00:11", false));

    assertEquals(1, update.getAddOperations().size());
    assertTrue(update.getAddOperations().containsKey("mac"));
  }

  @Test
  public void testGetRenameAndDeleteOperations() {
    CollectionSchemaUpdate update = CollectionSchemaUpdate.update("oldName", new RenameOperation("newName"))
        .set("obsolete", new DeleteOperation());

    assertEquals(1, update.getRenameOperations().size());
    assertEquals("newName", update.getRenameOperations().get("oldName").getNewName());
    assertEquals(1, update.getDeleteOperations().size());
  }
}
