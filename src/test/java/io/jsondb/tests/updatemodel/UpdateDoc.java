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
package io.jsondb.tests.updatemodel;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;

/**
 * Has one property of each kind findAndModify has to convert an Update value into.
 */
@Document(collection = "updatedocs", schemaVersion= "1.0")
public class UpdateDoc {
  public enum Status {
    NEW, DONE
  }

  public static class Address {
    private String city;

    public String getCity() {
      return city;
    }
    public void setCity(String city) {
      this.city = city;
    }
  }

  @Id
  private String id;
  private String name;
  private long size;
  private int count;
  private boolean flash;
  private Integer boxed;
  private Status status;
  private Address address;

  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public long getSize() {
    return size;
  }
  public void setSize(long size) {
    this.size = size;
  }
  public int getCount() {
    return count;
  }
  public void setCount(int count) {
    this.count = count;
  }
  public boolean isFlash() {
    return flash;
  }
  public void setFlash(boolean flash) {
    this.flash = flash;
  }
  public Integer getBoxed() {
    return boxed;
  }
  public void setBoxed(Integer boxed) {
    this.boxed = boxed;
  }
  public Status getStatus() {
    return status;
  }
  public void setStatus(Status status) {
    this.status = status;
  }
  public Address getAddress() {
    return address;
  }
  public void setAddress(Address address) {
    this.address = address;
  }
}
