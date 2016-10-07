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
package io.jsondb.query;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Farooq Khan
 * @version 1.0 21 Aug 2016
 */
public class Update {

  private Map<String, Object> updateData;

  public Update() {
    updateData = new TreeMap<String, Object>();
  }

  /**
   * Static factory method to create an Update using the provided key
   *
   * @param key the field name for the update operation
   * @param value  the value to set for the field
   * @return  Updated object
   */
  public static Update update(String key, Object value) {
    return new Update().set(key, value);
  }

  public Update set(String key, Object value) {
    updateData.put(key, value);
    return this;
  }

  public Map<String, Object> getUpdateData() {
    return updateData;
  }
}
