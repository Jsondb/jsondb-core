/*
 * Copyright (c) 2016 Farooq Khan
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
package io.jsondb.tests.model;

import java.util.List;
import java.util.UUID;

import io.jsondb.annotation.Document;
import io.jsondb.annotation.Id;

/**
 * @version 1.0 10-Dec-2017
 */
@Document(collection = "pojowithlist", schemaVersion = "1.0")
public class PojoWithList {
  @Id
  private String id;
  
  private List<String> stuff;

  public PojoWithList() {
    this.id = UUID.randomUUID().toString();
  }

  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
  }

  public List<String> getStuff() {
    return stuff;
  }
  public void setStuff(List<String> stuff) {
    this.stuff = stuff;
  } 
}
