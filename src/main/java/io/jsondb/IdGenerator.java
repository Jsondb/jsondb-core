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

/**
 * Supplies the id for a document that is inserted or upserted without one, in place of JsonDB's
 * built-in ids (see {@link io.jsondb.annotation.Id}). Set one for a class with
 * {@link JsonDBConfig#setIdGenerator(Class, IdGenerator)}, or for every collection with
 * {@link JsonDBConfig#setIdGenerator(IdGenerator)}.
 *
 * <p>Calls for one collection never overlap, because they happen while its write lock is held. A
 * generator shared by several collections can be called from several threads at once, so it must be
 * thread-safe.</p>
 *
 * <p>JsonDB rejects, with {@link InvalidJsonDbApiUsageException} and before anything is saved, an id
 * that is null, not of the @Id field's type, or already in the collection. It does not ask again;
 * making ids unique is the generator's job.</p>
 */
@FunctionalInterface
public interface IdGenerator {
  /**
   * @param collectionName the collection the document is saved to
   * @param document the document being saved, e.g. to build an id from its fields
   * @return the new id, of the @Id field's type
   */
  Object generateId(String collectionName, Object document);
}
