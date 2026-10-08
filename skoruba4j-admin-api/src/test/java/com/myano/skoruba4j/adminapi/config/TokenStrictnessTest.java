package com.myano.skoruba4j.adminapi.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenStrictnessTest {

  @Test
  void defaultAllowsCompatibleClients() {
    assertFalse(new IdserverProperties().strictTokens());
  }

  @Test
  void apiUiEnabledByDefault() {
    assertTrue(new IdserverProperties().apiUiEnabled());
  }

  @Test
  void strictMeansExpiredAccessTokenRequiresLogin() {
    IdserverProperties properties = new IdserverProperties();
    properties.getLogout().setEndSession("strict");
    assertTrue(properties.strictTokens());
  }
}
