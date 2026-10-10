package com.myano.skoruba4j.domain.configstore;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight failure counter for {@link AuditLogWriter} write errors. Exposed for metrics /
 * health-check consumption (e.g. T0-3 Actuator {@code health} indicator or Prometheus gauge).
 *
 * <p>Thread-safe, zero dependencies. Counter never resets during JVM lifetime — use rate-of-change
 * sampling for alerting rather than absolute threshold.
 */
public final class AuditFailureMeter {
  private static final AtomicInteger FAILURES = new AtomicInteger();
  private static volatile long lastFailureTimeMs;

  private AuditFailureMeter() {}

  static void record(RuntimeException ex) {
    FAILURES.incrementAndGet();
    lastFailureTimeMs = System.currentTimeMillis();
  }

  /** Total audit write failures since JVM start. */
  public static int failureCount() {
    return FAILURES.get();
  }

  /** Timestamp (epoch ms) of the most recent failure, or 0 if never failed. */
  public static long lastFailureTimeMs() {
    return lastFailureTimeMs;
  }

  /** True if any audit write has failed during this JVM lifetime. */
  public static boolean hasFailed() {
    return FAILURES.get() > 0;
  }
}
