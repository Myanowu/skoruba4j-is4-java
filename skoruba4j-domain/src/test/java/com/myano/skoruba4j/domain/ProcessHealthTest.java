package com.myano.skoruba4j.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcessHealthTest {

  @Test
  void reportsProcessWithoutDatabaseOrSecrets() {
    Map<String, String> body =
        ProcessHealth.snapshot("skoruba4j-sts", "http://127.0.0.1:5050", Optional.empty());
    assertEquals("UP", body.get("status"));
    assertEquals("skoruba4j-sts", body.get("process"));
    assertEquals("http://127.0.0.1:5050", body.get("issuer"));
    assertEquals("not-configured", body.get("database"));
  }
}
