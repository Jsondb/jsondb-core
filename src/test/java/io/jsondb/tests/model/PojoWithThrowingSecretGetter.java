package io.jsondb.tests.model;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;
import io.jsondb.annotation.Secret;

@Document(collection = "throwingsecretgetter", schemaVersion = "1.0")
public class PojoWithThrowingSecretGetter {
  @Id
  private String id;

  @Secret
  private String token;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getToken() {
    throw new RuntimeException("secret getter failed");
  }

  public void setToken(String token) {
    this.token = token;
  }
}
