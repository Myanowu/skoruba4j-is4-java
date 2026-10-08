package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class Is4LogoutResponseHandlerTest {

  @Test
  void formLoginAuthenticationHasNoPostLogoutRedirect() {
    assertFalse(
        Is4LogoutResponseHandler.hasPostLogoutRedirect(
            UsernamePasswordAuthenticationToken.authenticated("user", "n/a", java.util.List.of())));
  }
}
