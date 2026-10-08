package com.myano.skoruba4j.sts.security.externallogin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthorizeClientIdsTest {

  @Test
  void parsesClientIdFromAuthorizeReturnUrl() {
    assertEquals(
        "skoruba4j-admin",
        AuthorizeClientIds.fromUrl(
                "/connect/authorize?client_id=skoruba4j-admin&response_type=code&scope=openid")
            .orElseThrow());
  }

  @Test
  void emptyWhenMissing() {
    assertTrue(AuthorizeClientIds.fromUrl("/login").isEmpty());
  }
}
