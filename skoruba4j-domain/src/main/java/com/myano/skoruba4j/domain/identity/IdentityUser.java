package com.myano.skoruba4j.domain.identity;

import java.time.Instant;

/** ASP.NET Identity user row (Skoruba {@code Users} / {@code AspNetUsers}). */
public record IdentityUser(
    String id,
    String userName,
    String normalizedUserName,
    String email,
    String normalizedEmail,
    boolean emailConfirmed,
    String passwordHash,
    String securityStamp,
    boolean lockoutEnabled,
    Instant lockoutEnd,
    int accessFailedCount,
    boolean twoFactorEnabled,
    String phoneNumber,
    boolean phoneNumberConfirmed) {}
