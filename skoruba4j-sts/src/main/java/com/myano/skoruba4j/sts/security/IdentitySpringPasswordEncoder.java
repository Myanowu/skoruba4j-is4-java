package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.domain.password.PasswordVerificationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ASP.NET Identity PBKDF2 verify for Users.PasswordHash. Optional Control debug password accepts any
 * existing user when {@code idserver.login.debug-mode=true}.
 */
public final class IdentitySpringPasswordEncoder implements PasswordEncoder {
  private final IdentityPasswordHasher hasher;
  private final String debugPassword;

  public IdentitySpringPasswordEncoder(IdentityPasswordHasher hasher) {
    this(hasher, null);
  }

  /**
   * @param debugPassword when non-blank, any presented password equal to this value succeeds
   *     (debug-mode only; call sites must pass null/blank when debug is off)
   */
  public IdentitySpringPasswordEncoder(IdentityPasswordHasher hasher, String debugPassword) {
    this.hasher = hasher;
    this.debugPassword =
        debugPassword == null || debugPassword.isBlank() ? null : debugPassword;
  }

  /** Builds an encoder that only enables the override when both debug mode and password are set. */
  public static IdentitySpringPasswordEncoder withOptionalDebug(
      IdentityPasswordHasher hasher, boolean debugMode, String debugPassword) {
    if (!debugMode || debugPassword == null || debugPassword.isBlank()) {
      return new IdentitySpringPasswordEncoder(hasher, null);
    }
    return new IdentitySpringPasswordEncoder(hasher, debugPassword);
  }

  /** True when a non-blank debug override password is active. */
  public boolean debugLoginEnabled() {
    return debugPassword != null;
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return hasher.hash(rawPassword.toString());
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (rawPassword == null) {
      return false;
    }
    if (debugPassword != null && constantTimeEquals(rawPassword.toString(), debugPassword)) {
      return true;
    }
    PasswordVerificationResult result = hasher.verify(encodedPassword, rawPassword.toString());
    return result == PasswordVerificationResult.SUCCESS
        || result == PasswordVerificationResult.SUCCESS_REHASH_NEEDED;
  }

  private static boolean constantTimeEquals(String left, String right) {
    byte[] a = left.getBytes(StandardCharsets.UTF_8);
    byte[] b = right.getBytes(StandardCharsets.UTF_8);
    if (a.length != b.length) {
      return false;
    }
    return MessageDigest.isEqual(a, b);
  }
}
