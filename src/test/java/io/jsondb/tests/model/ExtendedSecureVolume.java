package io.jsondb.tests.model;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;
import io.jsondb.annotation.Secret;

@Document(collection = "extendedsecurevolumes", schemaVersion = "1.0")
public class ExtendedSecureVolume extends BaseSecureVolume {
  @Id
  private String id;
  private String zone;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }
}
