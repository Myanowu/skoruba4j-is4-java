package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryScopesTest {

  @Test
  void alwaysIncludesOidcBasicsThenDatabaseNames() {
    List<String> merged = DiscoveryScopes.merge(List.of("api1", "openid"));
    assertEquals("openid", merged.get(0));
    assertTrue(merged.contains("profile"));
    assertTrue(merged.contains("email"));
    assertTrue(merged.contains("offline_access"));
    assertTrue(merged.contains("api1"));
  }
}
