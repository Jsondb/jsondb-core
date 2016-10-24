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
package io.jsondb.query.ddl;

import io.jsondb.query.ddl.CollectionSchemaUpdate.Type;

/**
 * Represents a CollectionUpdate ADD operation type.
 *
 * This operation allows for adding a new field to a POJO
 * 
 * @author Farooq Khan
 * @version 1.0 21 Aug 2016
 */
public class AddOperation extends AbstractOperation {
  private Object defaultValue;
  private boolean isSecret;

  public AddOperation(Object defaultValue, boolean isSecret) {
    this.operationType = Type.ADD;
    this.defaultValue = defaultValue;
    this.isSecret = isSecret;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public boolean isSecret() {
    return isSecret;
  }
}
