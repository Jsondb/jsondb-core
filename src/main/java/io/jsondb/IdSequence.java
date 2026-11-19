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
package io.jsondb;

import java.util.Collection;
import java.util.UUID;

/**
 * Hands out the ids for the documents of one insert or upsert that are saved without one. A numeric
 * {@link io.jsondb.annotation.Id} (Long, Integer, long or int) gets the next number after the highest
 * id in the collection and in the batch; any other gets a random UUID.
 *
 * The numbers are not stored anywhere but in the documents, so when the document with the highest id
 * is removed, that id is handed out again.
 */
final class IdSequence {
  private final Class<?> idType;
  private final long maxValue;
  private long last;

  /**
   * @param idType the type of the collection's @Id field
   * @param existingIds the ids already in the collection
   */
  IdSequence(Class<?> idType, Collection<?> existingIds) {
    this.idType = idType;
    this.maxValue = isIntegerType(idType) ? Integer.MAX_VALUE : Long.MAX_VALUE;
    for (Object id : existingIds) {
      reserve(id);
    }
  }

  /**
   * Makes sure the given id, e.g. one set explicitly on a document of the batch, is never handed out
   */
  void reserve(Object id) {
    if (isNumeric(idType) && id instanceof Number) {
      last = Math.max(last, ((Number) id).longValue());
    }
  }

  /**
   * @throws InvalidJsonDbApiUsageException if a numeric id type has no number left
   */
  Object next() {
    if (!isNumeric(idType)) {
      return UUID.randomUUID().toString();
    }
    if (last >= maxValue) {
      throw new InvalidJsonDbApiUsageException("Cannot generate an id: there is no id left after " + last
          + " for an @Id of type " + idType.getSimpleName());
    }
    last++;
    if (isIntegerType(idType)) {
      return Integer.valueOf((int) last);
    }
    return Long.valueOf(last);
  }

  /**
   * @return true if the id means the document has none yet: null, or 0 for a primitive numeric @Id,
   *     which cannot be null
   */
  static boolean isMissing(Object id, Class<?> idType) {
    if (null == id) {
      return true;
    }
    return idType != null && idType.isPrimitive() && isNumeric(idType) && ((Number) id).longValue() == 0;
  }

  private static boolean isNumeric(Class<?> type) {
    return type == Long.class || type == long.class || isIntegerType(type);
  }

  private static boolean isIntegerType(Class<?> type) {
    return type == Integer.class || type == int.class;
  }
}
