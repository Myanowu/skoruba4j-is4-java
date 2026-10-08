package com.myano.skoruba4j.protocol;

/**
 * Token and RP-logout strictness. One switch: expired tokens are either tolerated (IS4 drop-in) or
 * rejected so the client must sign in again.
 *
 * <p>{@code compatible} matches IdentityServer4 (refresh tokens, 60s JWT skew, lenient
 * {@code /connect/endsession}). {@code strict} rejects expired JWTs with no skew, does not issue
 * refresh tokens, and uses Spring Authorization Server logout rules (authorization store + {@code
 * sid}) after the expiry check.
 */
public enum EndSessionMode {
  COMPATIBLE,
  STRICT;

  public static EndSessionMode fromConfig(String raw) {
    if (raw != null && "strict".equalsIgnoreCase(raw.trim())) {
      return STRICT;
    }
    return COMPATIBLE;
  }

  public boolean isCompatible() {
    return this == COMPATIBLE;
  }

  public boolean isStrict() {
    return this == STRICT;
  }
}
