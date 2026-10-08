package com.myano.skoruba4j.sts.security.externallogin;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse {@code LOGIN <code>} (and wa.me {@code LOGIN%<code>}) from inbound WhatsApp text. */
public final class WhatsAppLoginMessages {
  private static final Pattern LOGIN =
      Pattern.compile("(?i)\\bLOGIN[%\\s:_-]*([A-Z0-9]{6,16})\\b");

  private WhatsAppLoginMessages() {}

  public static String prefillText(String code) {
    return "LOGIN " + (code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
  }

  public static Optional<String> extractCode(String body) {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    Matcher m = LOGIN.matcher(body.trim());
    if (!m.find()) {
      return Optional.empty();
    }
    return Optional.of(m.group(1).toUpperCase(Locale.ROOT));
  }
}
