package com.myano.skoruba4j.domain.identity;

import java.time.Instant;

/** Editable Identity user profile fields (Skoruba User Profile / Admin API Put). */
public record UserProfileWrite(
    String userName,
    String email,
    boolean emailConfirmed,
    String phoneNumber,
    boolean phoneNumberConfirmed,
    boolean lockoutEnabled,
    Instant lockoutEnd,
    int accessFailedCount,
    boolean twoFactorEnabled) {}
