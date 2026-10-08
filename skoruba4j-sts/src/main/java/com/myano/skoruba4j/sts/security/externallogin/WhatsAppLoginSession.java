package com.myano.skoruba4j.sts.security.externallogin;

import java.time.Instant;

/** In-memory WhatsApp QR login attempt (scan → reply LOGIN &lt;code&gt;). */
public final class WhatsAppLoginSession {
  public enum Status {
    PENDING,
    READY,
    NEED_CONFIRM,
    FAILED,
    CONSUMED
  }

  private final String code;
  private final String clientId;
  private final Instant createdAt;
  private final Instant expiresAt;
  private volatile Status status;
  private volatile String phoneDigits;
  private volatile String userId;
  private volatile PendingExternalLogin pending;
  private volatile String errorKey;

  public WhatsAppLoginSession(String code, String clientId, Instant createdAt, Instant expiresAt) {
    this.code = code;
    this.clientId = clientId;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.status = Status.PENDING;
  }

  public String code() {
    return code;
  }

  public String clientId() {
    return clientId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Status status() {
    return status;
  }

  public String phoneDigits() {
    return phoneDigits;
  }

  public String userId() {
    return userId;
  }

  public PendingExternalLogin pending() {
    return pending;
  }

  public String errorKey() {
    return errorKey;
  }

  public boolean expired(Instant now) {
    return now != null && expiresAt != null && now.isAfter(expiresAt);
  }

  public synchronized void markReady(String phoneDigits, String userId) {
    if (status != Status.PENDING) {
      return;
    }
    this.phoneDigits = phoneDigits;
    this.userId = userId;
    this.status = Status.READY;
  }

  public synchronized void markNeedConfirm(String phoneDigits, PendingExternalLogin pending) {
    if (status != Status.PENDING) {
      return;
    }
    this.phoneDigits = phoneDigits;
    this.pending = pending;
    this.status = Status.NEED_CONFIRM;
  }

  public synchronized void markFailed(String errorKey) {
    if (status != Status.PENDING) {
      return;
    }
    this.errorKey = errorKey == null || errorKey.isBlank() ? "external" : errorKey;
    this.status = Status.FAILED;
  }

  public synchronized boolean consume() {
    if (status != Status.READY && status != Status.NEED_CONFIRM) {
      return false;
    }
    this.status = Status.CONSUMED;
    return true;
  }
}
