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
package io.jsondb.tests.querypath;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Class shapes used only by ReflectionPropertyHandlerTests. They are registered with the handler,
 * which is global JXPath state, so no other test may use them.
 */
public final class HandlerShapes {
  private HandlerShapes() {
  }

  public static class Leaf {
    private String value;

    public String getValue() {
      return value;
    }
    public void setValue(String value) {
      this.value = value;
    }
  }

  public static class ArrayItem extends Leaf {
  }

  public static class MapValue extends Leaf {
  }

  public static class WildcardItem extends Leaf {
  }

  public enum Kind {
    SMALL, LARGE
  }

  /**
   * Reaches each nested class through a different kind of declared type
   */
  public static class Root {
    private String URL;
    private String readOnly = "fixed";
    private ArrayItem[] array;
    private Map<String, MapValue> map;
    private List<? extends WildcardItem> wildcard;
    private Kind kind;
    private LocalDate date;

    public String getURL() {
      return URL;
    }
    public void setURL(String url) {
      this.URL = url;
    }
    public String getReadOnly() {
      return readOnly;
    }
    public ArrayItem[] getArray() {
      return array;
    }
    public void setArray(ArrayItem[] array) {
      this.array = array;
    }
    public Map<String, MapValue> getMap() {
      return map;
    }
    public void setMap(Map<String, MapValue> map) {
      this.map = map;
    }
    public List<? extends WildcardItem> getWildcard() {
      return wildcard;
    }
    public void setWildcard(List<? extends WildcardItem> wildcard) {
      this.wildcard = wildcard;
    }
    public Kind getKind() {
      return kind;
    }
    public void setKind(Kind kind) {
      this.kind = kind;
    }
    public LocalDate getDate() {
      return date;
    }
    public void setDate(LocalDate date) {
      this.date = date;
    }
  }

  public static class Throwing {
    public String getBroken() {
      throw new IllegalStateException("getter failed");
    }
    public void setBroken(String value) {
      throw new IllegalStateException("setter failed");
    }
  }
}
