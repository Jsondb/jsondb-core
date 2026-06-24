package io.jsondb.tests.model;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;

@Document(collection = "throwingGetId", schemaVersion = "1.0")
public class PojoForThrowingGetIdTest {
  @Id
  private String id;

  public PojoForThrowingGetIdTest(String id) {
    this.id = id;
  }

  public String getId() {
    throw new RuntimeException("getter failed");
  }

  public void setId(String id) {
    this.id = id;
  }
}
