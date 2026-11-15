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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.jxpath.DynamicPropertyHandler;
import org.apache.commons.jxpath.JXPathIntrospector;

/**
 * Reflection-based {@link DynamicPropertyHandler} used on platforms without {@code java.beans.Introspector}
 * (such as Android ART/Dalvik) so JXPath queries work without {@code NoClassDefFoundError}.
 */
public class ReflectionDynamicPropertyHandler implements DynamicPropertyHandler {

  private static final boolean JAVA_BEANS_AVAILABLE = checkJavaBeansAvailable();
  private static final ConcurrentHashMap<Class<?>, ClassProperties> CACHE =
      new ConcurrentHashMap<Class<?>, ClassProperties>();
  private static final Set<Class<?>> REGISTERED_CLASSES =
      Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

  public ReflectionDynamicPropertyHandler() {
  }

  static boolean isJavaBeansAvailable() {
    return JAVA_BEANS_AVAILABLE;
  }

  static void registerIfNeeded(Class<?> rootClass) {
    if (!JAVA_BEANS_AVAILABLE && rootClass != null) {
      registerHierarchy(rootClass, new HashSet<Class<?>>());
    }
  }

  private static void registerHierarchy(Class<?> clazz, Set<Class<?>> visited) {
    if (clazz == null || !visited.add(clazz) || isBuiltInType(clazz)) {
      return;
    }
    if (REGISTERED_CLASSES.add(clazz)) {
      JXPathIntrospector.registerDynamicClass(clazz, ReflectionDynamicPropertyHandler.class);
    }
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        registerType(field.getGenericType(), visited);
      }
      current = current.getSuperclass();
    }
  }

  private static void registerType(Type type, Set<Class<?>> visited) {
    if (type instanceof Class<?>) {
      Class<?> cls = (Class<?>) type;
      if (cls.isArray()) {
        registerType(cls.getComponentType(), visited);
      } else {
        registerHierarchy(cls, visited);
      }
    } else if (type instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) type;
      registerType(pt.getRawType(), visited);
      for (Type arg : pt.getActualTypeArguments()) {
        registerType(arg, visited);
      }
    }
  }

  private static boolean checkJavaBeansAvailable() {
    try {
      Class.forName("java.beans.Introspector", false, ReflectionDynamicPropertyHandler.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  private static boolean isBuiltInType(Class<?> clazz) {
    if (clazz.isPrimitive() || clazz.isEnum() || clazz.isArray()) {
      return true;
    }
    if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
      return true;
    }
    String name = clazz.getName();
    return name.startsWith("java.")
        || name.startsWith("javax.")
        || name.startsWith("sun.")
        || name.startsWith("android.")
        || name.startsWith("androidx.");
  }

  @Override
  public String[] getPropertyNames(Object object) {
    if (object == null) {
      return new String[0];
    }
    return metadataFor(object.getClass()).propertyNames;
  }

  @Override
  public Object getProperty(Object object, String propertyName) {
    if (object == null || propertyName == null) {
      return null;
    }
    ClassProperties meta = metadataFor(object.getClass());
    Method getter = meta.getters.get(propertyName);
    Object value = null;
    if (getter != null) {
      try {
        value = getter.invoke(object);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to read property '" + propertyName + "' via getter on " + object.getClass().getName(), e);
      }
    } else {
      Field field = meta.fields.get(propertyName);
      if (field != null) {
        try {
          value = field.get(object);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Failed to read field '" + propertyName + "' on " + object.getClass().getName(), e);
        }
      }
    }
    if (value != null && !JAVA_BEANS_AVAILABLE && !isBuiltInType(value.getClass())) {
      registerHierarchy(value.getClass(), new HashSet<Class<?>>());
    }
    return value;
  }

  @Override
  public void setProperty(Object object, String propertyName, Object value) {
    if (object == null || propertyName == null) {
      return;
    }
    ClassProperties meta = metadataFor(object.getClass());
    Method setter = meta.setters.get(propertyName);
    if (setter != null) {
      try {
        setter.invoke(object, value);
        return;
      } catch (Exception e) {
        throw new IllegalStateException("Failed to set property '" + propertyName + "' via setter on " + object.getClass().getName(), e);
      }
    }
    Field field = meta.fields.get(propertyName);
    if (field != null) {
      try {
        field.set(object, value);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("Failed to set field '" + propertyName + "' on " + object.getClass().getName(), e);
      }
    }
  }

  private static ClassProperties metadataFor(Class<?> clazz) {
    ClassProperties existing = CACHE.get(clazz);
    if (existing != null) {
      return existing;
    }
    ClassProperties computed = inspect(clazz);
    ClassProperties previous = CACHE.putIfAbsent(clazz, computed);
    return previous != null ? previous : computed;
  }

  private static ClassProperties inspect(Class<?> clazz) {
    Map<String, Field> fields = new LinkedHashMap<String, Field>();
    Map<String, Method> getters = new LinkedHashMap<String, Method>();
    Map<String, Method> setters = new LinkedHashMap<String, Method>();
    Set<String> names = new HashSet<String>();

    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
          continue;
        }
        String name = field.getName();
        if (!fields.containsKey(name)) {
          trySetAccessible(field);
          fields.put(name, field);
          names.add(name);
        }
      }
      current = current.getSuperclass();
    }

    for (Method method : clazz.getMethods()) {
      if (method.getDeclaringClass() == Object.class || Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      String methodName = method.getName();
      if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
        String prop = extractPropertyName(methodName, "get");
        if (prop == null && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
          prop = extractPropertyName(methodName, "is");
        }
        if (prop != null && !"class".equals(prop)) {
          trySetAccessible(method);
          getters.putIfAbsent(prop, method);
          names.add(prop);
        }
      } else if (method.getParameterCount() == 1) {
        String prop = extractPropertyName(methodName, "set");
        if (prop != null) {
          trySetAccessible(method);
          setters.putIfAbsent(prop, method);
        }
      }
    }

    return new ClassProperties(names.toArray(new String[0]), fields, getters, setters);
  }

  private static String extractPropertyName(String methodName, String prefix) {
    if (!methodName.startsWith(prefix) || methodName.length() <= prefix.length()) {
      return null;
    }
    String tail = methodName.substring(prefix.length());
    if (tail.length() > 1 && Character.isUpperCase(tail.charAt(0)) && Character.isUpperCase(tail.charAt(1))) {
      return tail;
    }
    return Character.toLowerCase(tail.charAt(0)) + tail.substring(1);
  }

  private static void trySetAccessible(Field field) {
    try {
      field.setAccessible(true);
    } catch (RuntimeException ignored) {
    }
  }

  private static void trySetAccessible(Method method) {
    try {
      method.setAccessible(true);
    } catch (RuntimeException ignored) {
    }
  }

  private static final class ClassProperties {
    final String[] propertyNames;
    final Map<String, Field> fields;
    final Map<String, Method> getters;
    final Map<String, Method> setters;

    ClassProperties(
        String[] propertyNames,
        Map<String, Field> fields,
        Map<String, Method> getters,
        Map<String, Method> setters) {
      this.propertyNames = propertyNames;
      this.fields = fields;
      this.getters = getters;
      this.setters = setters;
    }
  }
}

