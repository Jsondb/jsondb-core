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

/**
 * An abstract adapter class for receiving Collection File Change events.
 * The methods in this class are empty.
 * This class exists as convenience for creating listener objects.
 *
 * Extend this class to create a Collection File Change listener and override only the methods for
 * the events of interest. (If you implement the CollectionFileChangeListener interface, you have to
 * define all of the methods in it. This abstract class defines null methods for them all, so you
 * can only have to define methods for events you care about.)
 *
 * @author Farooq Khan
 * @version 1.0 21 Aug 2016
 */
public abstract class CollectionFileChangeAdapter implements CollectionFileChangeListener {

  @Override
  public void collectionFileAdded(String collectionName) {  }

  @Override
  public void collectionFileDeleted(String collectionName) {  }

  @Override
  public void collectionFileModified(String collectionName) {  }

}
