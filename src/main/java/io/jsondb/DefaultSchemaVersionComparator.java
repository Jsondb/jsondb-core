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
package io.jsondb;

import java.util.Comparator;

/**
 * A default schema version comparator that expects the version to be in x.y.z form where each of the digits is
 * strictly a integer.
 *
 * @author Farooq Khan
 * @version 1.0 25-Sep-2016
 */
public class DefaultSchemaVersionComparator implements Comparator<String> {

  /**
   * compare the expected version with the actual version.
   * 
   * Checkout: http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
   * 
   * @param expected the version that is obtained from @Document annotation
   * @param actual the version that is read from the .json file
   * @return a negative integer, zero, or a positive integer as the first argument is less
   *         than, equal to, or greater than the second.
   */
  @Override
  public int compare(String expected, String actual) {
    String[] vals1 = expected.split("\\.");
    String[] vals2 = actual.split("\\.");

    int i = 0;
    while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
      i++;
    }

    if (i < vals1.length && i < vals2.length) {
      int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
      return Integer.signum(diff);
    } else {
      return Integer.signum(vals1.length - vals2.length);
    }
  }

}
