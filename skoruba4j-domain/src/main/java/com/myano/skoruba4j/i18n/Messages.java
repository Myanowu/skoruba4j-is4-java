package com.myano.skoruba4j.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** UTF-8 message bundles. Missing keys fall back to English. */
public final class Messages {
  private static final Map<UiLocale, Properties> BUNDLES = loadAll();

  private Messages() {}

  public static String t(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    String value = BUNDLES.get(UiLocale.current()).getProperty(key);
    if (value == null) {
      value = BUNDLES.get(UiLocale.EN).getProperty(key);
    }
    return value == null ? key : value;
  }

  public static String t(String key, Object... args) {
    String pattern = t(key);
    if (args == null || args.length == 0) {
      return pattern;
    }
    return MessageFormat.format(pattern, args);
  }

  private static Map<UiLocale, Properties> loadAll() {
    EnumMap<UiLocale, Properties> map = new EnumMap<>(UiLocale.class);
    map.put(UiLocale.EN, load("i18n/messages.properties"));
    map.put(UiLocale.ZH_HANS, load("i18n/messages_zh_Hans.properties"));
    map.put(UiLocale.ZH_HANT, load("i18n/messages_zh_Hant.properties"));
    return Map.copyOf(map);
  }

  private static Properties load(String path) {
    Properties properties = new Properties();
    try (InputStream in = Messages.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource " + path);
      }
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return properties;
  }
}
