package io.jsondb.tests;

import io.jsondb.CollectionMetaData;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBTemplate;
import io.jsondb.ReflectionDynamicPropertyHandler;
import io.jsondb.tests.model.Instance;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathIntrospector;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.reflections.Reflections;
import org.reflections.serializers.JsonSerializer;
import org.reflections.serializers.XmlSerializer;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests Android-style entity discovery where runtime directory/class scanning returns empty results
 * and pre-built {@code META-INF/reflections/*.xml} / {@code *.json} indexes or explicit entity classes are used.
 */
public class AndroidDiscoveryTests {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testDiscoversEntitiesFromXmlMetadataIndexWhenRuntimeScanIsEmpty() throws Exception {
    File indexFile = tempFolder.newFile("demo-reflections.xml");
    Reflections prescan = new Reflections(new ConfigurationBuilder()
        .forPackage("io.jsondb.tests.model")
        .filterInputsBy(new FilterBuilder().includePackage("io.jsondb.tests.model")));
    prescan.save(indexFile.getAbsolutePath(), new XmlSerializer());

    File indexJar = packageIndexJar("META-INF/reflections/demo-reflections.xml", indexFile);
    ClassLoader apkLikeLoader = new ApkSimulatedClassLoader(indexJar, Instance.class.getClassLoader());

    JsonDBConfig config = new JsonDBConfig(
        tempFolder.newFolder("db-xml").getAbsolutePath(),
        "io.jsondb.tests.model",
        null,
        false,
        null,
        null,
        apkLikeLoader);

    Map<String, CollectionMetaData> cmdMap = CollectionMetaData.builder(config);
    assertTrue("Expected 'instances' collection to be discovered from XML index", cmdMap.containsKey("instances"));
    assertFalse("Unrelated model classes outside baseScanPackage must not be loaded", cmdMap.isEmpty());

    JsonDBTemplate db = new JsonDBTemplate(config);
    db.createCollection(Instance.class);
    Instance instance = new Instance();
    instance.setId("android-1");
    instance.setHostname("pixel-demo");
    db.insert(instance);

    List<Instance> stored = db.findAll(Instance.class);
    assertEquals(1, stored.size());
    assertEquals("pixel-demo", stored.get(0).getHostname());
  }

  @Test
  public void testDiscoversEntitiesFromJsonMetadataIndexAndFiltersByBaseScanPackage() throws Exception {
    File indexFile = tempFolder.newFile("demo-reflections.json");
    Reflections prescan = new Reflections(new ConfigurationBuilder()
        .forPackage("io.jsondb.tests.model")
        .filterInputsBy(new FilterBuilder().includePackage("io.jsondb.tests.model")));
    prescan.save(indexFile.getAbsolutePath(), new JsonSerializer());

    File indexJar = packageIndexJar("META-INF/reflections/demo-reflections.json", indexFile);
    ClassLoader apkLikeLoader = new ApkSimulatedClassLoader(indexJar, Instance.class.getClassLoader());

    JsonDBConfig nonMatchingConfig = new JsonDBConfig(
        tempFolder.newFolder("db-mismatch").getAbsolutePath(),
        "com.example.other",
        null,
        false,
        null,
        null,
        apkLikeLoader);
    Map<String, CollectionMetaData> emptyMap = CollectionMetaData.builder(nonMatchingConfig);
    assertTrue("Entities outside baseScanPackage must be filtered out", emptyMap.isEmpty());

    JsonDBConfig matchingConfig = new JsonDBConfig(
        tempFolder.newFolder("db-json").getAbsolutePath(),
        "io.jsondb.tests.model",
        null,
        false,
        null,
        null,
        apkLikeLoader);
    Map<String, CollectionMetaData> cmdMap = CollectionMetaData.builder(matchingConfig);
    assertTrue("Expected 'instances' collection to be discovered from JSON index", cmdMap.containsKey("instances"));
  }

  @Test
  public void testExplicitEntityClassesWithoutRuntimeOrIndexScan() throws Exception {
    File dbDir = tempFolder.newFolder("db-explicit");
    ClassLoader emptyLoader = new ClassLoader(Instance.class.getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) {
        return Collections.emptyEnumeration();
      }
    };

    JsonDBTemplate db = new JsonDBTemplate(
        dbDir.getAbsolutePath(),
        "com.example.unscannable",
        null,
        false,
        null,
        Collections.singletonList(Instance.class),
        emptyLoader);

    db.createCollection(Instance.class);
    assertTrue(db.collectionExists("instances"));
    Instance item = new Instance();
    item.setId("explicit-1");
    item.setHostname("explicit-host");
    db.insert(item);
    assertEquals(1, db.findAll(Instance.class).size());
  }

  @Test
  public void testReflectionDynamicPropertyHandlerSupportsJxPathQueriesWithoutIntrospector() {
    ReflectionDynamicPropertyHandler handler = new ReflectionDynamicPropertyHandler();
    Instance instance = new Instance();
    handler.setProperty(instance, "id", "dyn-1");
    handler.setProperty(instance, "hostname", "android-art");
    assertEquals("dyn-1", handler.getProperty(instance, "id"));
    assertEquals("android-art", handler.getProperty(instance, "hostname"));

    JXPathIntrospector.registerDynamicClass(Instance.class, ReflectionDynamicPropertyHandler.class);
    JXPathContext ctx = JXPathContext.newContext(Collections.singletonList(instance));
    Object matched = ctx.getValue("/.[id='dyn-1']/hostname");
    assertEquals("android-art", matched);
  }

  private File packageIndexJar(String entryName, File contentFile) throws IOException {
    File jarFile = tempFolder.newFile(entryName.replace('/', '_'));
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile));
         FileInputStream fis = new FileInputStream(contentFile)) {
      JarEntry dirEntry = new JarEntry("META-INF/reflections/");
      jos.putNextEntry(dirEntry);
      jos.closeEntry();

      JarEntry fileEntry = new JarEntry(entryName);
      jos.putNextEntry(fileEntry);
      byte[] buffer = new byte[4096];
      int read;
      while ((read = fis.read(buffer)) != -1) {
        jos.write(buffer, 0, read);
      }
      jos.closeEntry();
    }
    return jarFile;
  }

  /**
   * Simulates an Android APK ClassLoader: runtime package/class directory queries return empty,
   * while resources inside {@code META-INF/reflections/} and classes by name remain resolvable.
   */
  private static final class ApkSimulatedClassLoader extends ClassLoader {
    private final URL jarDirectoryUrl;
    private final String resourceEntryPath;

    ApkSimulatedClassLoader(File jarFile, ClassLoader parent) throws IOException {
      super(parent);
      this.jarDirectoryUrl = new URL("jar:" + jarFile.toURI().toURL() + "!/META-INF/reflections/");
      this.resourceEntryPath = "META-INF/reflections/";
    }

    @Override
    public Enumeration<URL> getResources(String name) {
      if (resourceEntryPath.equals(name) || "META-INF/reflections".equals(name)) {
        return Collections.enumeration(Collections.singletonList(jarDirectoryUrl));
      }
      return Collections.emptyEnumeration();
    }

    @Override
    public URL getResource(String name) {
      if (resourceEntryPath.equals(name) || "META-INF/reflections".equals(name)) {
        return jarDirectoryUrl;
      }
      return null;
    }
  }
}
