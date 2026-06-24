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
