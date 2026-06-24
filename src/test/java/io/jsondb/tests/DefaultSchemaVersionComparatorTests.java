package io.jsondb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.jsondb.DefaultSchemaVersionComparator;

public class DefaultSchemaVersionComparatorTests {

  private final DefaultSchemaVersionComparator comparator = new DefaultSchemaVersionComparator();

  @Test
  public void testEqualVersions() {
    assertEquals(0, comparator.compare("1.0", "1.0"));
    assertEquals(0, comparator.compare("2.1.3", "2.1.3"));
  }

  @Test
  public void testDifferentSegmentCounts() {
    assertTrue(comparator.compare("1.0", "1.0.1") < 0);
    assertTrue(comparator.compare("1.0.0", "1.0") > 0);
    assertTrue(comparator.compare("2.0", "1.9.9") > 0);
  }

  @Test
  public void testDifferentPatchLevels() {
    assertTrue(comparator.compare("1.0.1", "1.0.2") < 0);
    assertTrue(comparator.compare("1.1.0", "1.0.9") > 0);
  }
}
