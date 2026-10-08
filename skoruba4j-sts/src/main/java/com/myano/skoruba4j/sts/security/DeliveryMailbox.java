package com.myano.skoruba4j.sts.security;

/** Matches STS Identity forgot-password rewrite of {@code local--tag@domain} to {@code local@domain}. */
public final class DeliveryMailbox {
  private DeliveryMailbox() {}

  public static String to(String storedEmail) {
    if (storedEmail == null || storedEmail.isBlank()) {
      return "";
    }
    String email = storedEmail.trim();
    int atIndex = email.indexOf('@');
    int dashIndex = email.indexOf("--");
    if (dashIndex >= 0 && (atIndex < 0 || dashIndex < atIndex)) {
      String domain = atIndex >= 0 ? email.substring(atIndex) : "";
      return email.substring(0, dashIndex) + domain;
    }
    return email;
  }
}
