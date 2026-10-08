package com.myano.skoruba4j.sts.security.externallogin;

import java.io.Serializable;

/** Held in session when Client link-mode is {@code confirm}. */
public record PendingExternalLogin(
    String loginProvider,
    String providerKey,
    String email,
    String suggestedUserName,
    String displayName)
    implements Serializable {
  private static final long serialVersionUID = 1L;
}
