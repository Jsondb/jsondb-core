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

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * @author Farooq Khan
 * @version 1.0 21 Aug 2016
 */
public class CollectionSchemaUpdate {

  public enum Type {ADD, RENAME, DELETE};

  private Map<String, IOperation> collectionUpdateData;

  public CollectionSchemaUpdate() {
    collectionUpdateData = new TreeMap<String, IOperation>();
  }

  /**
   * Static factory method to create an CollectionUpdate for the specified key
   *
   * @param key: JSON attribute to update
   * @param operation: operation to carry out on the attribute
   * @return  the updated CollectionSchemaUpdate
   */
  public static CollectionSchemaUpdate update(String key, IOperation operation) {
    return new CollectionSchemaUpdate().set(key, operation);
  }

  /**
   * A method to set a new Operation for a key. It may be of type ADD, RENAME or DELETE.
   * Only one operation per key can be specified. Attempt to add a second operation for a any key will override the first one.
   * Attempt to add a ADD operation for a key which already exists will have no effect.
   * Attempt to add a DELETE operation for akey which does not exist will have no effect.
   *
   * @param key (a.k.a JSON Field name) for which operation is being added
   * @param operation  operation to perform
   * @return  the updated CollectionSchemaUpdate
   */
  public CollectionSchemaUpdate set(String key, IOperation operation) {
    collectionUpdateData.put(key, operation);
    return this;
  }

  public Map<String, IOperation> getUpdateData() {
    return collectionUpdateData;
  }

  /**
   * Returns a Map of ADD operations which have a non-null default value specified.
   * 
   * @return Map of ADD operations which have a non-null  default value specified
   */
  public Map<String, AddOperation> getAddOperations() {
    Map<String, AddOperation> addOperations = new TreeMap<String, AddOperation>();
    for (Entry<String, IOperation> entry : collectionUpdateData.entrySet()) {
      String key = entry.getKey();
      IOperation op = entry.getValue();
      if (op.getOperationType().equals(Type.ADD)) {
        AddOperation aop = (AddOperation)op;
        if (null != aop.getDefaultValue()) {
          addOperations.put(key, aop);
        }
      }
    }
    return addOperations;
  }

  /**
   * Returns a Map of RENAME operations.
   * 
   * @return Map of RENAME operations which have a non-null  default value specified
   */
  public Map<String, RenameOperation> getRenameOperations() {
    Map<String, RenameOperation> renOperations = new TreeMap<String, RenameOperation>();
    for (Entry<String, IOperation> entry : collectionUpdateData.entrySet()) {
      String key = entry.getKey();
      IOperation op = entry.getValue();
      if (op.getOperationType().equals(Type.RENAME)) {
        renOperations.put(key, (RenameOperation)op);
      }
    }
    return renOperations;
  }

  /**
   * Returns a Map of DELETE operations.
   * 
   * @return Map of DELETE operations which have a non-null  default value specified
   */
  public Map<String, DeleteOperation> getDeleteOperations() {
    Map<String, DeleteOperation> delOperations = new TreeMap<String, DeleteOperation>();
    for (Entry<String, IOperation> entry : collectionUpdateData.entrySet()) {
      String key = entry.getKey();
      IOperation op = entry.getValue();
      if (op.getOperationType().equals(Type.DELETE)) {
        delOperations.put(key, (DeleteOperation)op);
      }
    }
    return delOperations;
  }
}
