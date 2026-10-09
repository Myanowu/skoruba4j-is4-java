package com.myano.skoruba4j.domain.configstore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditLogWriterTest {

  @Test
  void blankToDash() {
    assertEquals("-", AuditLogWriter.blankToDash(null));
    assertEquals("-", AuditLogWriter.blankToDash("  "));
    assertEquals("alice", AuditLogWriter.blankToDash(" alice "));
  }

  @Test
  void writeWithEmptyJdbcIsNoOp() {
    assertDoesNotThrow(
        () ->
            AuditLogWriter.loginFailure(Optional.empty(), "alice", "bad credentials"));
    assertDoesNotThrow(() -> AuditLogWriter.loginSuccess(Optional.empty(), "alice"));
    assertDoesNotThrow(
        () -> AuditLogWriter.tokenIssued(Optional.empty(), "sub", "client", "password"));
    assertDoesNotThrow(() -> AuditLogWriter.tokenFailure(Optional.empty(), "sub", "invalid_grant"));
    assertDoesNotThrow(() -> AuditLogWriter.adminRequest(Optional.empty(), "admin", "POST /x"));
  }
}
