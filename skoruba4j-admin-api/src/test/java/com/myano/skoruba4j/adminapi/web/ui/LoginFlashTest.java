package com.myano.skoruba4j.adminapi.web.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginFlashTest {

  @Test
  void hidesSpringClassNames() {
    String raw =
        "No AuthenticationProvider found for org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken";
    assertTrue(LoginFlash.looksTechnical(raw));
    String flash = LoginFlash.forQuery("oauth2_error", raw, "MyRole");
    assertFalse(flash.contains("org.springframework"));
    assertFalse(flash.contains("AuthenticationProvider"));
    assertEquals(LoginFlash.oauthSignInFailed(), flash);
  }

  @Test
  void hidesClassNameInErrorParam() {
    String raw =
        "No AuthenticationProvider found for org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken";
    String flash = LoginFlash.forQuery(raw, null, "MyRole");
    assertFalse(flash.contains("org.springframework"));
    assertEquals(LoginFlash.oauthSignInFailed(), flash);
  }

  @Test
  void passwordErrorStaysSimple() {
    assertEquals("Invalid username or password.", LoginFlash.forQuery("true", null, "MyRole"));
  }

  @Test
  void deniedMessageIsShort() {
    assertEquals(
        "This account cannot use the API console. Sign in with an admin account.",
        LoginFlash.adminAccessDenied());
  }
}
