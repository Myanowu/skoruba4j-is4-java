package com.myano.skoruba4j.sts.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginPageTest {

  @Test
  void postsToLoginWithUsernamePasswordAndCsrf() {
    String html = LoginPage.render(true, false, "_csrf", "abc&1", "/connect/authorize?client_id=a");
    assertTrue(html.contains("action=\"/login?ReturnUrl=%2Fconnect%2Fauthorize%3Fclient_id%3Da\""));
    assertTrue(html.contains("name=\"username\""));
    assertTrue(html.contains("name=\"password\""));
    assertTrue(html.contains("name=\"_csrf\""));
    assertTrue(html.contains("name=\"ReturnUrl\""));
    assertTrue(html.contains("value=\"/connect/authorize?client_id=a\""));
    assertTrue(html.contains("value=\"abc&amp;1\""));
    assertTrue(html.contains("Invalid username or password"));
    assertTrue(html.contains("Forgot your password?"));
    assertTrue(html.contains("href=\"/forgot-password\""));
    assertTrue(html.contains("class=\"card\""));
    assertTrue(html.contains("Skoruba4j STS"));
    assertTrue(html.contains("OpenID Provider"));
    assertTrue(html.contains("toggleSecret"));
    assertTrue(!html.contains("Sign in with Google"));
    assertTrue(!html.contains("cshtml"));
  }

  @Test
  void showsGoogleWhenEnabled() {
    String html =
        LoginPage.render(
            false, false, "_csrf", "t", "/connect/authorize?client_id=a", null, true, false);
    assertTrue(html.contains("Sign in with Google"));
    assertTrue(html.contains("/oauth2/authorization/google"));
  }

  @Test
  void showsMicrosoftWhenEnabled() {
    String html =
        LoginPage.render(
            false, false, "_csrf", "t", "/connect/authorize?client_id=a", null, false, true);
    assertTrue(html.contains("Sign in with Microsoft"));
    assertTrue(html.contains("/oauth2/authorization/microsoft"));
  }

  @Test
  void showsWhatsAppWhenEnabled() {
    String html =
        LoginPage.render(
            false, false, "_csrf", "t", "/connect/authorize?client_id=a", null, false, false, true);
    assertTrue(html.contains("Sign in with WhatsApp"));
    assertTrue(html.contains("/external/whatsapp"));
    assertTrue(!html.contains("qr.png"));
  }

  @Test
  void embedsWhatsAppQrWhenCodeProvided() {
    String html =
        LoginPage.render(
            false,
            false,
            "_csrf",
            "t",
            "/connect/authorize?client_id=a",
            null,
            false,
            false,
            true,
            false,
            "ABCD2345",
            "LOGIN ABCD2345");
    assertTrue(html.contains("/external/whatsapp/qr.png?code=ABCD2345"));
    assertTrue(html.contains("LOGIN ABCD2345"));
    assertTrue(html.contains("Scan with WhatsApp"));
  }

  @Test
  void showsWeChatWhenEnabled() {
    String html =
        LoginPage.render(
            false,
            false,
            "_csrf",
            "t",
            "/connect/authorize?client_id=a",
            null,
            false,
            false,
            false,
            true);
    assertTrue(html.contains("Sign in with WeChat"));
    assertTrue(html.contains("/external/wechat"));
  }

  @Test
  void externalErrorCopy() {
    String html =
        LoginPage.render(
            false, false, "_csrf", "t", null, "external-no-account", false, false);
    assertTrue(html.contains("No local account matches this external sign-in"));
  }

  @Test
  void signedInPageShowsUserId() {
    String html =
        SignedInPage.render("guid-1", "alice", "a@b.c", false, java.util.List.of(), null);
    assertTrue(html.contains("Signed in"));
    assertTrue(html.contains("guid-1"));
    assertTrue(html.contains("alice"));
    assertTrue(html.contains("/logout"));
    assertTrue(html.contains("/account/password"));
    assertTrue(html.contains("openGrants()"));
    assertTrue(html.contains("grantsHelp"));
    assertTrue(html.contains("PersistedGrants"));
    assertTrue(html.contains("Change password"));
    assertTrue(html.contains("External sign-ins"));
    assertTrue(html.contains("openJson('/.well-known/openid-configuration'"));
    assertTrue(html.contains("openJson('/health'"));
    assertTrue(html.contains("jsonDlg"));
  }
}
