/*
 * Copyright (c) 2016 - 2018 Farooq Khan
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
package io.jsondb.tests.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Scanner;

import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsIterableContainingInOrder;

/**
 * @author Farooq Khan
 * @version 1.0 06-Oct-2016
 */
public class TestUtils {
  public static void checkLastLines(File jsonFile, String[] expectedLinesAtEnd) {
    CircularQueue<String> queue = new CircularQueue<String>(expectedLinesAtEnd.length);

    Scanner sc = null;
    try {
      sc = new Scanner(jsonFile, "UTF-8");
      while (sc.hasNextLine()) {
        queue.add(sc.nextLine());
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } finally {
      if (null != sc) {
        sc.close();
      }
    }
    MatcherAssert.assertThat(queue, IsIterableContainingInOrder.contains(expectedLinesAtEnd));
  }

  public static boolean appendDirectToFile(File file, String data) {
    boolean retval = false;
    FileWriter fw = null;
    try {
      fw = new FileWriter(file, true);
      fw.write(data);
      fw.write("\n");
      retval = true;
    } catch (IOException e) {
      retval = false;
      e.printStackTrace();
    } finally {
      if (null != fw) {
        try {
          fw.close();
        } catch (IOException e) {
          System.out.println(e);
        }
      }
    }
    return retval;
  }
  
  public static int getNoOfLinesInFile(File file) {
    int lines = 0;
    FileReader f = null;
    BufferedReader reader =  null;
    try {
      f = new FileReader(file);
      reader = new BufferedReader(f);
      while (reader.readLine() != null) {
        lines++;
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (null != reader) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (null != f) {
        try {
          f.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return lines;
  }
  
  @SuppressWarnings("serial")
  public static class CircularQueue<E> extends LinkedList<E> {
    private int limit;

    public CircularQueue(int limit) {
        this.limit = limit;
    }

    @Override
    public boolean add(E o) {
        super.add(o);
        while (size() > limit) { super.remove(); }
        return true;
    }
  }
  
  public static boolean isMac() {
    String OS = System.getProperty("os.name").toLowerCase();
    return (OS.indexOf("mac") >= 0);
  }
}
