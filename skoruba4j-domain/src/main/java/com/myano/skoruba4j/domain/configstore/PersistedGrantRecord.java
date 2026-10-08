package com.myano.skoruba4j.domain.configstore;

import java.time.Instant;

/** IS4 {@code PersistedGrants} row (Admin detail columns). */
public record PersistedGrantRecord(
    String key,
    String type,
    String subjectId,
    String clientId,
    String sessionId,
    String description,
    String data,
    Instant creationTime,
    Instant expiration,
    Instant consumedTime) {

  /** Compact constructor used by older list callers that only need core columns. */
  public PersistedGrantRecord(
      String key,
      String type,
      String subjectId,
      String clientId,
      Instant creationTime,
      Instant expiration) {
    this(key, type, subjectId, clientId, null, null, null, creationTime, expiration, null);
  }
}
