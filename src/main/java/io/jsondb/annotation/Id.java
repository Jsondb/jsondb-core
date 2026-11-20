/*
 * Copyright (c) 2016 - 2026 Farooq Khan
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
package io.jsondb.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the field that holds a document's id. Every {@link Document} class needs one.
 *
 * <p>When a document is inserted or upserted without an id, JsonDB generates one. An
 * {@link io.jsondb.IdGenerator} set for the collection, see
 * {@link io.jsondb.JsonDBConfig#setIdGenerator(Class, io.jsondb.IdGenerator)}, supplies it; otherwise:</p>
 * <ul>
 *   <li>a {@code Long}, {@code Integer}, {@code long} or {@code int} id gets the next number after the
 *       highest id in the collection, starting at 1. For {@code long} and {@code int}, which cannot be
 *       null, 0 means no id.</li>
 *   <li>a {@code String} id gets a random UUID.</li>
 * </ul>
 *
 * <p>Numbers are not stored anywhere but in the documents, so they are not a database sequence: when
 * the document with the highest id is removed, the next insert gets that id again. Ids are handed out
 * safely to threads sharing one JsonDBTemplate, but not to several processes writing the same folder.</p>
 *
 * @version 1.0 25-Sep-2016
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { FIELD, METHOD, ANNOTATION_TYPE })
public @interface Id {
}
