package com.myano.skoruba4j.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TotpCodesTest {

  @Test
  void roundTripBase32AndVerifyKnownVector() {
    // RFC 6238 Appendix B seed "12345678901234567890" as ASCII → Base32
    byte[] seed = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    String key = TotpCodes.encodeBase32(seed);
    // At T=59 (unix), timestep 1 → code 287082 per RFC 6238 test vector for SHA1
    assertTrue(TotpCodes.verify(key, "287082", 59L));
    assertFalse(TotpCodes.verify(key, "000000", 59L));
  }

  @Test
  void generateKeyIsVerifiable() {
    String key = TotpCodes.generateKey();
    long now = System.currentTimeMillis() / 1000L;
    int code = TotpCodes.computeCode(TotpCodes.decodeBase32(key), now / 30);
    String formatted = String.format("%06d", code);
    assertTrue(TotpCodes.verify(key, formatted, now));
  }

  @Test
  void otpAuthUriContainsSecret() {
    String uri = TotpCodes.otpAuthUri("Skoruba4j", "demo@example.com", "JBSWY3DPEHPK3PXP");
    assertTrue(uri.startsWith("otpauth://totp/"));
    assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
    assertEquals(true, uri.contains("issuer=Skoruba4j"));
  }
}
