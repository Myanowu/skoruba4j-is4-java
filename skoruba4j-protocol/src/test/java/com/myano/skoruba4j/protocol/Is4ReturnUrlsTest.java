package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Is4ReturnUrlsTest {

  @Test
  void onlyAuthorizePathsAreSafe() {
    assertTrue(Is4ReturnUrls.isSafe("/connect/authorize"));
    assertTrue(Is4ReturnUrls.isSafe("/connect/authorize?client_id=a&redirect_uri=https://localhost:6061/signin-oidc"));
    assertTrue(
        Is4ReturnUrls.isSafe(
            "https://localhost:5051/connect/authorize?client_id=a&redirect_uri=https://localhost:6061/signin-oidc"));
    assertTrue(Is4ReturnUrls.isSafe("/connect/authorize/callback?client_id=a"));
    assertFalse(Is4ReturnUrls.isSafe("https://evil.example/steal"));
    assertFalse(Is4ReturnUrls.isSafe("//evil.example"));
    assertFalse(Is4ReturnUrls.isSafe("/logout"));
    assertFalse(Is4ReturnUrls.isSafe("/connect/authorize/../../etc/passwd"));
    assertFalse(Is4ReturnUrls.isSafe(null));
  }
}
