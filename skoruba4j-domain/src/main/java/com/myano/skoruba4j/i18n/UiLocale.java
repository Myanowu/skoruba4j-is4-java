package com.myano.skoruba4j.i18n;

import java.util.Locale;
import java.util.Optional;

/** UI languages for step 1: English, Simplified Chinese, Traditional Chinese. */
public enum UiLocale {
  EN("en", "English", Locale.ENGLISH),
  ZH_HANS("zh-Hans", "简体中文", Locale.forLanguageTag("zh-Hans")),
  ZH_HANT("zh-Hant", "繁體中文", Locale.forLanguageTag("zh-Hant"));

  public static final String COOKIE = "skoruba4j_lang";

  private static final ThreadLocal<UiLocale> CURRENT = ThreadLocal.withInitial(() -> EN);
  private static final ThreadLocal<String> REQUEST_PATH = ThreadLocal.withInitial(() -> "/");

  private final String code;
  private final String label;
  private final Locale locale;

  UiLocale(String code, String label, Locale locale) {
    this.code = code;
    this.label = label;
    this.locale = locale;
  }

  public String code() {
    return code;
  }

  public String label() {
    return label;
  }

  public Locale locale() {
    return locale;
  }

  public String htmlLang() {
    return code;
  }

  public static UiLocale current() {
    UiLocale value = CURRENT.get();
    return value == null ? EN : value;
  }

  public static void setCurrent(UiLocale locale) {
    CURRENT.set(locale == null ? EN : locale);
  }

  public static void clearCurrent() {
    CURRENT.remove();
    REQUEST_PATH.remove();
  }

  /** Current request path for language-switcher return links. */
  public static void setRequestPath(String path) {
    REQUEST_PATH.set(path == null || path.isBlank() ? "/" : path);
  }

  public static String requestPath() {
    String path = REQUEST_PATH.get();
    return path == null || path.isBlank() ? "/" : path;
  }

  public static Optional<UiLocale> parse(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    String normalized = code.trim().replace('_', '-');
    if (normalized.equalsIgnoreCase("en") || normalized.toLowerCase(Locale.ROOT).startsWith("en-")) {
      return Optional.of(EN);
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.startsWith("zh-hant")
        || lower.startsWith("zh-tw")
        || lower.startsWith("zh-hk")
        || lower.startsWith("zh-mo")) {
      return Optional.of(ZH_HANT);
    }
    if (lower.startsWith("zh")) {
      return Optional.of(ZH_HANS);
    }
    return Optional.empty();
  }

  public static UiLocale resolve(String cookieValue, String acceptLanguage) {
    return parse(cookieValue).or(() -> firstFromAcceptLanguage(acceptLanguage)).orElse(EN);
  }

  static Optional<UiLocale> firstFromAcceptLanguage(String header) {
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    for (String part : header.split(",")) {
      String tag = part.trim();
      int q = tag.indexOf(';');
      if (q >= 0) {
        tag = tag.substring(0, q).trim();
      }
      Optional<UiLocale> match = parse(tag);
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }
}
