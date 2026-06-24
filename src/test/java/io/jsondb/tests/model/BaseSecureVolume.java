package io.jsondb.tests.model;

import io.jsondb.annotation.Secret;

public abstract class BaseSecureVolume {
  @Secret
  private String encryptionKey;

  public String getEncryptionKey() {
    return encryptionKey;
  }

  public void setEncryptionKey(String encryptionKey) {
    this.encryptionKey = encryptionKey;
  }
}
