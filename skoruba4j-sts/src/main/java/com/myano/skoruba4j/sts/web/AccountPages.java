package com.myano.skoruba4j.sts.web;

final class AccountPages {
  private AccountPages() {}

  static String forgot(String error) {
    StringBuilder form = new StringBuilder();
    form.append(StsPages.notice("err", error));
    form.append("<form method=\"post\" action=\"/forgot-password\">");
    form.append("<label for=\"email\">Email</label>");
    form.append(StsPages.field("email", "email", "email", "email", true));
    form.append("<button class=\"primary\" type=\"submit\">Send reset link</button>");
    form.append("</form>");
    form.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Back to sign in</a></p>");
    return StsPages.document(
        "Forgot password",
        "auth",
        StsPages.card(
            "Skoruba4j STS",
            "Forgot password",
            "Enter the email on the account. If it exists, we send a reset link.",
            form.toString()));
  }

  static String forgotConfirmation() {
    String inner =
        "<p class=\"muted\">If that account exists, we sent a password reset link.</p>"
            + "<a class=\"btn\" href=\"/login\">Back to sign in</a>";
    return StsPages.document(
        "Forgot password",
        "auth",
        StsPages.card("Skoruba4j STS", "Check your email", "", inner));
  }

  static String reset(String code, String error) {
    StringBuilder form = new StringBuilder();
    form.append(StsPages.notice("err", error));
    form.append("<form method=\"post\" action=\"/reset-password\">");
    form.append(StsPages.hidden("code", code == null ? "" : code));
    form.append("<label for=\"email\">Email</label>");
    form.append(StsPages.field("email", "email", "email", "email", true));
    form.append("<label for=\"password\">New password</label>");
    form.append(StsPages.passwordField("password", "password", "new-password", false));
    form.append("<label for=\"confirmPassword\">Confirm password</label>");
    form.append(StsPages.passwordField("confirmPassword", "confirmPassword", "new-password", false));
    form.append("<button class=\"primary\" type=\"submit\">Reset password</button>");
    form.append("</form>");
    form.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Back to sign in</a></p>");
    return StsPages.document(
        "Reset password",
        "auth",
        StsPages.card("Skoruba4j STS", "Reset password", "Choose a new password for this account.", form.toString()));
  }

  static String resetConfirmation() {
    String inner =
        "<p class=\"muted\">Your password was reset. You can sign in now.</p>"
            + "<a class=\"btn\" href=\"/login\">Back to sign in</a>";
    return StsPages.document(
        "Reset password",
        "auth",
        StsPages.card("Skoruba4j STS", "Password reset", "", inner));
  }

  static String invalidReset() {
    String inner =
        "<p class=\"muted\">This reset link is missing or no longer valid.</p>"
            + "<a class=\"btn\" href=\"/forgot-password\">Request a new link</a>";
    return StsPages.document(
        "Reset password",
        "auth",
        StsPages.card("Skoruba4j STS", "Reset link is not valid", "", inner));
  }

  /** Shown when self-registration is off. The setting sits on its own line so the key does not wrap mid-word. */
  static String registerDisabled(String productName) {
    String kicker =
        productName == null || productName.isBlank() ? "Skoruba4j STS" : productName.trim();
    String inner =
        "<p class=\"muted\">Self-registration is turned off. Turn it on in the STS config or Control Settings, then restart STS.</p>"
            + "<code class=\"setting\">idserver.login.allow-register=true</code>"
            + "<a class=\"btn\" href=\"/login\">Back to sign in</a>";
    return StsPages.document(
        "Register",
        "auth",
        StsPages.card(kicker, "Registration is disabled", "", inner));
  }

  static String register(String productName, String error, String captchaPrompt) {
    StringBuilder form = new StringBuilder();
    form.append(StsPages.notice("err", error));
    form.append("<form method=\"post\" action=\"/register\">");
    form.append("<label for=\"userName\">Username</label>");
    form.append(StsPages.field("userName", "userName", "text", "username", true));
    form.append("<label for=\"email\">Email</label>");
    form.append(StsPages.field("email", "email", "email", "email", false));
    form.append("<label for=\"password\">Password</label>");
    form.append(StsPages.passwordField("password", "password", "new-password", false));
    form.append("<label for=\"confirmPassword\">Confirm password</label>");
    form.append(StsPages.passwordField("confirmPassword", "confirmPassword", "new-password", false));
    if (captchaPrompt != null && !captchaPrompt.isBlank()) {
      form.append("<label for=\"captcha\">").append(StsPages.esc(captchaPrompt)).append("</label>");
      form.append(StsPages.field("captcha", "captcha", "text", "off", false));
    }
    form.append("<button class=\"primary\" type=\"submit\">Create account</button>");
    form.append("</form>");
    form.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/login\">Back to sign in</a></p>");
    String kicker =
        productName == null || productName.isBlank() ? "Skoruba4j STS" : productName.trim();
    return StsPages.document(
        "Register",
        "auth",
        StsPages.card(kicker, "Create an account", "Self-registration for this STS.", form.toString()));
  }

  static String changePassword(String error, String ok) {
    StringBuilder form = new StringBuilder();
    form.append(StsPages.notice("err", error));
    form.append(StsPages.notice("ok", ok));
    form.append("<form method=\"post\" action=\"/account/password\">");
    form.append("<label for=\"currentPassword\">Current password</label>");
    form.append(StsPages.passwordField("currentPassword", "currentPassword", "current-password", true));
    form.append("<label for=\"password\">New password</label>");
    form.append(StsPages.passwordField("password", "password", "new-password", false));
    form.append("<label for=\"confirmPassword\">Confirm password</label>");
    form.append(StsPages.passwordField("confirmPassword", "confirmPassword", "new-password", false));
    form.append("<button class=\"primary\" type=\"submit\">Update password</button>");
    form.append("</form>");
    form.append("<p class=\"muted\" style=\"margin-top:1rem\"><a href=\"/\">Back to home</a></p>");
    return StsPages.document(
        "Change password",
        "auth",
        StsPages.card("Skoruba4j STS", "Change password", "New hashes are written in Identity v3 format.", form.toString()));
  }
}
