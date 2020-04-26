/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.innodb.java.reader.config;

import com.google.common.base.MoreObjects;

import com.alibaba.innodb.java.reader.exception.ReaderException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

/**
 * A specific system property that is used to configure various aspects of the framework.
 * <p>
 *
 * @author Acknowledge Apache Calcite.
 *
 * @param <T> the type of the property value
 */
public final class ReaderSystemProperty<T> {

  public static final String PROP_PREFIX = "innodb.java.reader.";

  /**
   * Holds all system properties related with the Calcite.
   */
  private static final Properties PROPERTIES = loadProperties();

  /**
   * Whether to enable innodb file length check to see if it can be divided by page size.
   */
  public static final ReaderSystemProperty<Boolean> ENABLE_IBD_FILE_LENGTH_CHECK =
      booleanProperty("innodb.java.reader.enable.file.length.check", false);

  /**
   * Whether to enable throwing exception when reading mysql8.0 new lob page, because
   * currently this does not implement yet.
   */
  public static final ReaderSystemProperty<Boolean> ENABLE_THROW_EXCEPTION_FOR_UNSUPPORTED_MYSQL80_LOB =
      booleanProperty("innodb.java.reader.enable.throw.exception.for.unsupported.mysql80.lob",
          true);

  private static ReaderSystemProperty<Boolean> booleanProperty(String key,
                                                               boolean defaultValue) {
    // Note that "" -> true (convenient for command-lines flags like '-Dflag')
    return new ReaderSystemProperty<>(key,
        v -> v == null ? defaultValue
            : "".equals(v) || Boolean.parseBoolean(v));
  }

  private static ReaderSystemProperty<Integer> intProperty(String key, int defaultValue) {
    return intProperty(key, defaultValue, v -> true);
  }

  /**
   * Returns the value of the system property with the specified name as int, or
   * the <code>defaultValue</code> if any of the conditions below hold:
   *
   * <ol>
   * <li>the property is not defined;
   * <li>the property value cannot be transformed to an int;
   * <li>the property value does not satisfy the checker.
   * </ol>
   */
  private static ReaderSystemProperty<Integer> intProperty(String key, int defaultValue,
                                                           IntPredicate valueChecker) {
    return new ReaderSystemProperty<>(key, v -> {
      if (v == null) {
        return defaultValue;
      }
      try {
        int intVal = Integer.parseInt(v);
        return valueChecker.test(intVal) ? intVal : defaultValue;
      } catch (NumberFormatException nfe) {
        return defaultValue;
      }
    });
  }

  private static ReaderSystemProperty<String> stringProperty(String key, String defaultValue) {
    return new ReaderSystemProperty<>(key, v -> v == null ? defaultValue : v);
  }

  private static ReaderSystemProperty<String> stringProperty(
      String key,
      String defaultValue,
      Set<String> allowedValues) {
    return new ReaderSystemProperty<>(key, v -> {
      if (v == null) {
        return defaultValue;
      }
      String normalizedValue = v.toUpperCase(Locale.ROOT);
      return allowedValues.contains(normalizedValue) ? normalizedValue : defaultValue;
    });
  }

  private static Properties loadProperties() {
    Properties properties = new Properties();
    ClassLoader classLoader = MoreObjects.firstNonNull(
        Thread.currentThread().getContextClassLoader(),
        ReaderSystemProperty.class.getClassLoader());
    // Read properties from the file "innodb-java-reader.properties", if it exists in classpath
    try (InputStream stream = classLoader.getResourceAsStream("innodb-java-reader.properties")) {
      if (stream != null) {
        properties.load(stream);
      }
    } catch (IOException e) {
      throw new ReaderException("While reading from innodb-java-reader.properties file", e);
    }

    final Properties allProperties = new Properties();
    Stream.concat(
        properties.entrySet().stream(),
        System.getProperties().entrySet().stream())
        .forEach(prop -> {
          String key = (String) prop.getKey();
          if (key.startsWith(PROP_PREFIX)) {
            allProperties.setProperty(key, (String) prop.getValue());
          }
        });

    return allProperties;
  }

  private final T value;

  private ReaderSystemProperty(String key, Function<String, T> valueParser) {
    this.value = valueParser.apply(PROPERTIES.getProperty(key));
  }

  /**
   * Returns the value of this property.
   *
   * @return the value of this property or <code>null</code> if a default value has not been
   * defined for this property.
   */
  public T value() {
    return value;
  }
}