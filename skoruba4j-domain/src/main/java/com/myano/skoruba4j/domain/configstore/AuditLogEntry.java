package com.myano.skoruba4j.domain.configstore;

import java.time.Instant;

/** Skoruba {@code AuditLog} row. */
public record AuditLogEntry(
    long id,
    String event,
    String source,
    String category,
    String subjectIdentifier,
    String subjectName,
    String subjectType,
    String subjectAdditionalData,
    String action,
    String data,
    Instant created) {}
