package com.myano.skoruba4j.adminapi.web.ui;

/** User-facing login errors — never dump exception class names onto the page. */
public final class LoginFlash {
  private LoginFlash() {}

  public static String oauthSignInFailed() {
    return "Sign-in with STS did not finish. Try again, or use username and password.";
  }

  public static String adminAccessDenied() {
    return "This account cannot use the API console. Sign in with an admin account.";
  }

  public static String invalidPassword(String adminRole) {
    return "Invalid username or password.";
  }

  /**
   * Map {@code /login?error=} query values to a short sentence. Technical {@code
   * error_description} (Spring class names, stack-like text) is ignored.
   */
  public static String forQuery(String error, String description, String adminRole) {
    if (error == null) {
      return null;
    }
    if (looksTechnical(error)) {
      return oauthSignInFailed();
    }
    if (error.isBlank() || "true".equalsIgnoreCase(error)) {
      return invalidPassword(adminRole);
    }
    if (looksTechnical(description)) {
      description = null;
    }
    String code = error.trim().toLowerCase().replace('_', '-');
    return switch (code) {
      case "access-denied", "access_denied" -> adminAccessDenied();
      case "invalid-grant", "invalid_grant" ->
          "The sign-in session expired. Try Google or Microsoft again.";
      case "login-required", "interaction-required" -> "Please sign in again.";
      case "oauth2-error", "oauth2_error", "server-error", "temporarily-unavailable" ->
          oauthSignInFailed();
      default -> {
        if (code.contains("oauth") || code.contains("oidc") || code.contains("openid")) {
          yield oauthSignInFailed();
        }
        if (description != null && !description.isBlank() && !looksTechnical(description)) {
          yield description.trim();
        }
        yield oauthSignInFailed();
      }
    };
  }

  static boolean looksTechnical(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    String t = text;
    if (t.length() > 180) {
      return true;
    }
    return t.contains("org.springframework")
        || t.contains("java.")
        || t.contains("AuthenticationProvider")
        || t.contains("AuthenticationException")
        || t.contains("OAuth2Login")
        || t.contains("Exception:")
        || t.contains("ProviderNotFound")
        || t.contains("class ")
        || t.indexOf('.') > 0 && t.contains("found for");
  }
}
