package com.myano.skoruba4j.domain.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentityPasswordHasherTest {
  /** Documented demo password only — not a production credential. */
  private static final String PASSWORD = "Passw0rd!";

  /**
   * V2 / V3 payloads for {@link #PASSWORD} with salt {@code 00112233445566778899aabbccddeeff},
   * produced with the same PBKDF2 parameters as ASP.NET Identity.
   */
  private static final String V2 =
      "AAARIjNEVWZ3iJmqu8zd7v9PCfdKlcyiRdgREeargUkb3YOfBwVlCBbO9F61zR8W3Q==";
  private static final String V3_100K =
      "AQAAAAEAAYagAAAAEAARIjNEVWZ3iJmqu8zd7v9Qj72U/KBAAYuuV6mhTnXYWn5NX2KMHhSFrNVgN8UMow==";
  private static final String V3_10K =
      "AQAAAAEAACcQAAAAEAARIjNEVWZ3iJmqu8zd7v9HURJhUjujOMscTAGIMYgVWzToHqSvkd1DOt/dk0X8kg==";

  private final IdentityPasswordHasher hasher = new IdentityPasswordHasher();

  @Test
  void verifiesIdentityV2AndAsksForRehash() {
    assertEquals(
        PasswordVerificationResult.SUCCESS_REHASH_NEEDED, hasher.verify(V2, PASSWORD));
    assertEquals(PasswordVerificationResult.FAILED, hasher.verify(V2, "wrong"));
  }

  @Test
  void verifiesIdentityV3FullIterations() {
    assertEquals(PasswordVerificationResult.SUCCESS, hasher.verify(V3_100K, PASSWORD));
    assertEquals(PasswordVerificationResult.FAILED, hasher.verify(V3_100K, "wrong"));
  }

  @Test
  void olderV3IterationCountNeedsRehash() {
    assertEquals(
        PasswordVerificationResult.SUCCESS_REHASH_NEEDED, hasher.verify(V3_10K, PASSWORD));
  }

  @Test
  void newHashIsV3AndRoundTrips() {
    String hash = hasher.hash(PASSWORD);
    assertEquals(PasswordVerificationResult.SUCCESS, hasher.verify(hash, PASSWORD));
    assertEquals(PasswordVerificationResult.FAILED, hasher.verify(hash, "Passw0rd"));
    assertTrue(hash.startsWith("AQAAAAEAAYag")); // version 0x01, PRF SHA256, 100000 iters
    assertNotEquals(hash, hasher.hash(PASSWORD));
  }

  @Test
  void rejectsGarbage() {
    assertEquals(PasswordVerificationResult.FAILED, hasher.verify(null, PASSWORD));
    assertEquals(PasswordVerificationResult.FAILED, hasher.verify("not-base64!!!", PASSWORD));
    assertEquals(PasswordVerificationResult.FAILED, hasher.verify("", PASSWORD));
    assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
  }
}
