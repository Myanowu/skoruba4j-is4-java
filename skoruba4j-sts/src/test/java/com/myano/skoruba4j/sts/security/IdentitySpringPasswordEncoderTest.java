package com.myano.skoruba4j.sts.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import org.junit.jupiter.api.Test;

class IdentitySpringPasswordEncoderTest {

  private final IdentityPasswordHasher hasher = new IdentityPasswordHasher();

  /** Real Identity hash still works when debug is off. */
  @Test
  void matchesStoredHashWithoutDebug() {
    String hash = hasher.hash("real-secret");
    IdentitySpringPasswordEncoder encoder =
        IdentitySpringPasswordEncoder.withOptionalDebug(hasher, false, "debug-pass");
    assertTrue(encoder.matches("real-secret", hash));
    assertFalse(encoder.matches("debug-pass", hash));
    assertFalse(encoder.debugLoginEnabled());
  }

  /** Debug mode + password lets any user authenticate with that password. */
  @Test
  void debugPasswordAcceptsAnyUser() {
    String hash = hasher.hash("real-secret");
    IdentitySpringPasswordEncoder encoder =
        IdentitySpringPasswordEncoder.withOptionalDebug(hasher, true, "debug-pass");
    assertTrue(encoder.debugLoginEnabled());
    assertTrue(encoder.matches("debug-pass", hash));
    assertTrue(encoder.matches("real-secret", hash));
    assertFalse(encoder.matches("wrong", hash));
  }

  /** Debug mode without a password does not open a backdoor. */
  @Test
  void debugModeBlankPasswordIsDisabled() {
    IdentitySpringPasswordEncoder encoder =
        IdentitySpringPasswordEncoder.withOptionalDebug(hasher, true, "  ");
    assertFalse(encoder.debugLoginEnabled());
  }
}
