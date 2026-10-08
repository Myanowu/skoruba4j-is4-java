package com.myano.skoruba4j.console.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeHealthClientTest {

  @Test
  void loopbackOnlyForLocalHosts() {
    assertTrue(NodeHealthClient.isLoopback("https://localhost:5051/health"));
    assertTrue(NodeHealthClient.isLoopback("http://127.0.0.1:5050/health"));
    assertFalse(NodeHealthClient.isLoopback("https://example.com/health"));
  }

  @Test
  void summarizeParsesProcessHealthJson() {
    NodeHealthClient.Summary up =
        NodeHealthClient.summarize(
            "{\"status\":\"UP\",\"process\":\"sts\",\"issuer\":\"https://localhost:5051\",\"database\":\"UP\"}");
    assertTrue(up.up());
    assertEquals("UP", up.database());
    assertTrue(up.line().contains("db UP"));

    NodeHealthClient.Summary dbDown =
        NodeHealthClient.summarize(
            "{\"status\":\"UP\",\"process\":\"sts\",\"database\":\"DOWN\"}");
    assertFalse(dbDown.up());
    assertTrue(dbDown.line().toLowerCase().contains("database"));

    NodeHealthClient.Summary unreachable = NodeHealthClient.summarize("down: ConnectException");
    assertFalse(unreachable.up());
    assertEquals("down: ConnectException", unreachable.detail());
  }
}
