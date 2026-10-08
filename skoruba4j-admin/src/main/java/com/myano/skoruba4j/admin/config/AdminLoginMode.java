package com.myano.skoruba4j.admin.config;

import java.util.Locale;

/**
 * How Admin authenticates interactive users.
 *
 * <ul>
 *   <li>{@link #LOCAL} — password against Users table (+ optional STS OIDC button)
 *   <li>{@link #STS_PASSWORD} — Admin form; server calls STS {@code grant_type=password} (+ STS OIDC)
 *   <li>{@link #STS_OIDC} — browser redirect to STS only (cross-app SSO)
 *   <li>{@link #BOTH} — local password form + STS OIDC (same as LOCAL for sign-in UI)
 * </ul>
 */
public enum AdminLoginMode {
  LOCAL,
  STS_PASSWORD,
  STS_OIDC,
  BOTH;

  public static AdminLoginMode fromConfig(String raw, boolean oidcEnabledFallback) {
    if (raw != null && !raw.isBlank()) {
      String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
      return switch (key) {
        case "local", "password", "form" -> LOCAL;
        case "sts-password", "stspassword", "ropc", "password-grant" -> STS_PASSWORD;
        case "sts-oidc", "oidc", "sso", "sts" -> STS_OIDC;
        case "both", "all", "hybrid" -> BOTH;
        default -> oidcEnabledFallback ? STS_OIDC : LOCAL;
      };
    }
    return oidcEnabledFallback ? STS_OIDC : LOCAL;
  }

  public String configValue() {
    return switch (this) {
      case LOCAL -> "local";
      case STS_PASSWORD -> "sts-password";
      case STS_OIDC -> "sts-oidc";
      case BOTH -> "both";
    };
  }

  /** OIDC client registration /oauth2/authorization/sts is available. */
  public boolean usesOidcClientBeans() {
    return this == STS_OIDC || this == BOTH || this == LOCAL || this == STS_PASSWORD;
  }

  public boolean showsPasswordForm() {
    return this == LOCAL || this == STS_PASSWORD || this == BOTH;
  }
}
