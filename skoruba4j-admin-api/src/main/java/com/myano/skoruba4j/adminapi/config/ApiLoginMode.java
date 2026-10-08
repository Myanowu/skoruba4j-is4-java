package com.myano.skoruba4j.adminapi.config;

import java.util.Locale;

/** Browser UI sign-in for Admin API. */
public enum ApiLoginMode {
  /** Identity username/password form only. */
  LOCAL,
  /** Redirect to STS (Google / Microsoft / WhatsApp / WeChat QR appear on STS for this client). */
  STS_OIDC,
  /** Form + STS OIDC button. */
  BOTH;

  public static ApiLoginMode fromConfig(String raw) {
    if (raw == null || raw.isBlank()) {
      return BOTH;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    return switch (key) {
      case "local", "password", "form" -> LOCAL;
      case "sts-oidc", "oidc", "sso", "sts" -> STS_OIDC;
      case "both", "all", "hybrid" -> BOTH;
      default -> BOTH;
    };
  }

  public boolean showsPasswordForm() {
    return this == LOCAL || this == BOTH;
  }

  public boolean usesStsOidc() {
    return this == STS_OIDC || this == BOTH;
  }

  public String configValue() {
    return switch (this) {
      case LOCAL -> "local";
      case STS_OIDC -> "sts-oidc";
      case BOTH -> "both";
    };
  }
}
