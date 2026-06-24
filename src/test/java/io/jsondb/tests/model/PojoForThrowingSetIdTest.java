package io.jsondb.tests.model;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;

@Document(collection = "throwingSetId", schemaVersion = "1.0")
public class PojoForThrowingSetIdTest {
  @Id
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    throw new RuntimeException("setter failed");
  }
}
