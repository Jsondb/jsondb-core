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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jxpath.DynamicPropertyHandler;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.JXPathIntrospector;

/**
 * Lets JXPath read document properties without java.beans, which Android does not have.
 *
 * JXPath normally finds a class's properties through java.beans.Introspector. When java.beans is
 * missing, JsonDB registers each document class, and the classes reachable through its properties,
 * with this handler instead. It follows the same JavaBeans conventions as Introspector (public
 * getX()/isX() getters, Introspector's decapitalization, names in sorted order) so queries return the
 * same results either way. Unlike the java.beans path it never exposes getClass() as a property.
 * Enums and JDK types such as java.time.LocalDate are registered as atomic values, so they are
 * compared by value rather than navigated into.
 *
 * Idea and analysis credit: Hatzen, in jsondb-core pull request #66.
 */
public class ReflectionPropertyHandler implements DynamicPropertyHandler {
  private static final boolean JAVA_BEANS_AVAILABLE = isClassPresent("java.beans.Introspector");

  private static final Map<Class<?>, BeanProperties> PROPERTIES = new ConcurrentHashMap<Class<?>, BeanProperties>();
  private static final Set<Class<?>> REGISTERED = new HashSet<Class<?>>();

  /**
   * Instantiated by JXPath for every registered class
   */
  public ReflectionPropertyHandler() {
  }

  /**
   * @return true when java.beans is available and JXPath can use its normal bean introspection
   */
  public static boolean isJavaBeansAvailable() {
    return JAVA_BEANS_AVAILABLE;
  }

  /**
   * Registers the class with this handler only when java.beans is missing, for example on Android.
   *
   * @param documentClass a class annotated with {@link io.jsondb.annotation.Document}
   */
  public static void registerIfJavaBeansMissing(Class<?> documentClass) {
    if (!JAVA_BEANS_AVAILABLE) {
      register(documentClass);
    }
  }

  /**
   * Makes JXPath use this handler for the class and for every class reachable through its properties.
   * JsonDB calls this itself when needed; it is public so the behaviour can be tested on a JVM that
   * has java.beans.
   *
   * @param documentClass a class annotated with {@link io.jsondb.annotation.Document}
   */
  public static void register(Class<?> documentClass) {
    synchronized (REGISTERED) {
      registerType(documentClass);
    }
  }

  private static void registerType(Type type) {
    if (type instanceof Class<?>) {
      Class<?> c = (Class<?>) type;
      if (c.isArray()) {
        registerType(c.getComponentType());
      } else {
        registerClass(c);
      }
    } else if (type instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) type;
      registerType(pt.getRawType());
      for (Type arg : pt.getActualTypeArguments()) {
        registerType(arg);
      }
    } else if (type instanceof GenericArrayType) {
      registerType(((GenericArrayType) type).getGenericComponentType());
    } else if (type instanceof WildcardType) {
      for (Type bound : ((WildcardType) type).getUpperBounds()) {
        registerType(bound);
      }
    }
  }

  private static void registerClass(Class<?> c) {
    if (c.isPrimitive() || Object.class.equals(c) || !REGISTERED.add(c)) {
      return;
    }
    if (Collection.class.isAssignableFrom(c) || Map.class.isAssignableFrom(c)) {
      // JXPath handles collections and maps itself, only their element types need registering
      return;
    }
    if (c.isEnum() || isPlatformClass(c)) {
      JXPathIntrospector.registerAtomicClass(c);
      return;
    }
    JXPathIntrospector.registerDynamicClass(c, ReflectionPropertyHandler.class);
    for (Method getter : propertiesOf(c).getters.values()) {
      registerType(getter.getGenericReturnType());
    }
  }

  private static boolean isPlatformClass(Class<?> c) {
    String name = c.getName();
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")
        || name.startsWith("kotlin.");
  }

  @Override
  public String[] getPropertyNames(Object object) {
    return propertiesOf(object.getClass()).names.clone();
  }

  @Override
  public Object getProperty(Object object, String propertyName) {
    Method getter = propertiesOf(object.getClass()).getters.get(propertyName);
    if (null == getter) {
      return null;
    }
    try {
      return getter.invoke(object);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new JXPathException("Cannot read property '" + propertyName + "' of " + object.getClass().getName(), e);
    }
  }

  @Override
  public void setProperty(Object object, String propertyName, Object value) {
    Method setter = propertiesOf(object.getClass()).setters.get(propertyName);
    if (null == setter) {
      throw new JXPathException("No writable property '" + propertyName + "' on " + object.getClass().getName());
    }
    try {
      setter.invoke(object, value);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new JXPathException("Cannot set property '" + propertyName + "' of " + object.getClass().getName(), e);
    }
  }

  private static BeanProperties propertiesOf(Class<?> c) {
    BeanProperties properties = PROPERTIES.get(c);
    if (null == properties) {
      properties = new BeanProperties(c);
      PROPERTIES.put(c, properties);
    }
    return properties;
  }

  /**
   * Same rule as java.beans.Introspector.decapitalize: "Hostname" becomes "hostname" but "URL" stays "URL"
   */
  static String decapitalize(String name) {
    if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static boolean isClassPresent(String className) {
    try {
      Class.forName(className, false, ReflectionPropertyHandler.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  private static final class BeanProperties {
    private final Map<String, Method> getters = new TreeMap<String, Method>();
    private final Map<String, Method> setters = new HashMap<String, Method>();
    private final String[] names;

    BeanProperties(Class<?> c) {
      Map<String, Method> isGetters = new HashMap<String, Method>();
      for (Method m : c.getMethods()) {
        if (Modifier.isStatic(m.getModifiers()) || m.isBridge() || m.isSynthetic()) {
          continue;
        }
        String name = m.getName();
        int paramCount = m.getParameterTypes().length;
        if (paramCount == 0 && name.startsWith("get") && name.length() > 3 && !void.class.equals(m.getReturnType())
            && !"getClass".equals(name)) {
          getters.put(decapitalize(name.substring(3)), accessible(m));
        } else if (paramCount == 0 && name.startsWith("is") && name.length() > 2 && boolean.class.equals(m.getReturnType())) {
          isGetters.put(decapitalize(name.substring(2)), accessible(m));
        } else if (paramCount == 1 && name.startsWith("set") && name.length() > 3) {
          setters.put(decapitalize(name.substring(3)), accessible(m));
        }
      }
      // Introspector prefers isX() over getX() for a boolean property
      getters.putAll(isGetters);
      names = getters.keySet().toArray(new String[0]);
    }

    private static Method accessible(Method m) {
      try {
        m.setAccessible(true);
      } catch (RuntimeException e) {
        // Public methods of public classes do not need it
      }
      return m;
    }
  }
}
