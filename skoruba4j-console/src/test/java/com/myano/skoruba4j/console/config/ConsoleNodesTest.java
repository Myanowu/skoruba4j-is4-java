package com.myano.skoruba4j.console.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsoleNodesTest {

  @Test
  void defaultsThreeLocalProcesses() {
    var nodes = ConsoleNodes.defaults();
    assertEquals(3, nodes.size());
    assertTrue(nodes.get("sts").isLocal());
    assertEquals("skoruba4j-sts", nodes.get("sts").getModule());
    assertTrue(nodes.get("sts").getHealthUrl().contains("/health"));
    assertTrue(nodes.get("sts").getHealthUrl().startsWith("https://"));
    assertEquals("Admin API", nodes.get("admin-api").getDisplayName());
  }
}
