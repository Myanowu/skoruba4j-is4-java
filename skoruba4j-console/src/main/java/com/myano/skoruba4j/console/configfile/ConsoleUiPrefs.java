package com.myano.skoruba4j.console.configfile;

import com.myano.skoruba4j.i18n.UiLocale;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists Control UI preferences (language) under {@code config/control-ui.properties}. */
public final class ConsoleUiPrefs {
  public static final String FILE_NAME = "control-ui.properties";
  private static final String KEY_LANG = "lang";

  private ConsoleUiPrefs() {}

  /** Resolve prefs file next to other install overlays. */
  public static Path resolve(Path installHome) {
    if (installHome == null) {
      return Path.of("config").resolve(FILE_NAME);
    }
    return installHome.resolve("config").resolve(FILE_NAME);
  }

  /** Load saved language, or English when missing / unreadable. */
  public static UiLocale load(Path installHome) {
    Path file = resolve(installHome);
    if (!Files.isRegularFile(file)) {
      return UiLocale.EN;
    }
    Properties props = new Properties();
    try (var in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
      props.load(in);
    } catch (IOException e) {
      return UiLocale.EN;
    }
    return UiLocale.parse(props.getProperty(KEY_LANG)).orElse(UiLocale.EN);
  }

  /** Write language choice; creates {@code config/} when needed. */
  public static void save(Path installHome, UiLocale locale) throws IOException {
    Path file = resolve(installHome);
    Files.createDirectories(file.getParent());
    Properties props = new Properties();
    if (Files.isRegularFile(file)) {
      try (var in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
        props.load(in);
      }
    }
    props.setProperty(KEY_LANG, locale == null ? UiLocale.EN.code() : locale.code());
    try (var out = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
      props.store(out, "Skoruba4j Control UI");
    }
  }
}
