package com.myano.skoruba4j.domain.externallogin;

/** Normalize phone numbers for WhatsApp / UserLogins matching. */
public final class PhoneNumbers {
  private PhoneNumbers() {}

  /** Digits only (no plus). Empty if blank. */
  public static String digitsOnly(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c >= '0' && c <= '9') {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** E.164-ish with leading {@code +}, or empty. */
  public static String toE164(String raw) {
    String digits = digitsOnly(raw);
    if (digits.isEmpty()) {
      return "";
    }
    return "+" + digits;
  }

  /** wa.me path segment (digits, no plus). */
  public static String waMeNumber(String e164OrRaw) {
    return digitsOnly(e164OrRaw);
  }
}
