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
package io.jsondb.events;

import java.util.EventListener;

/**
 * A listener which fires when a change to one or more files which have corresponding identified collections is detected.
 *
 * @author Farooq Khan
 * @version 1.0 21 Aug 2016
 */
public interface CollectionFileChangeListener extends EventListener {
  /**
   * Invoked when a new file is detected which has a corresponding identified collection.
   * A identified collection is that for which the package scan found a POJO with the @Document annotation.
   * This also means that such a identified collection did not have a corresponding collection file before.
   *
   * New files which do not have corresponding identified collection are ignored.
   *
   * @param collectionName the name of the collection for which a file was detected.
   */
  void collectionFileAdded(String collectionName);

  /**
   * Invoked when a file which has a corresponding identified collection is deleted
   *
   * Deleting files in the background is a dangerous operation after such a operation if the database is
   * reloaded that data will be permanently lost. However if any operation that modifies the database is
   * done then it will automatically recreate the file.
   *
   * @param collectionName the name of the collection for which a file was deleted.
   */
  void collectionFileDeleted(String collectionName);

  /**
   * Invoked when a file which has a corresponding identified collection is modified by program other than this one.
   *
   * Ideally you might want to reload the database on receiving this event. If you ignore and change to the database happens due to
   * a insert of update or any other Database mutating operation the file will be overwritten with the in memory state.
   *
   * @param collectionName the name of the collection whose corresponding file was modified.
   */
  void collectionFileModified(String collectionName);
}
