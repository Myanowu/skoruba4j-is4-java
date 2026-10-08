package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.identity.IdentityUser;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC reset codes (ASP.NET Identity Data Protection tokens are not portable). */
public final class PasswordResetTokens {
  public static final Duration TTL = Duration.ofHours(24);
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  private final byte[] key;
  private final Clock clock;

  public PasswordResetTokens(byte[] key, Clock clock) {
    this.key = key == null ? new byte[32] : key;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public static byte[] keyFrom(String resetKey, String issuerUri) {
    String material =
        resetKey != null && !resetKey.isBlank()
            ? resetKey.trim()
            : "skoruba4j-sts-reset|" + (issuerUri == null ? "" : issuerUri);
    try {
      return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String create(IdentityUser user) {
    if (user == null || user.id() == null || user.id().isBlank()) {
      throw new IllegalArgumentException("user");
    }
    long exp = clock.instant().plus(TTL).toEpochMilli();
    String mac = sign(user.id(), exp, stamp(user));
    return B64.encodeToString(user.id().getBytes(StandardCharsets.UTF_8))
        + "."
        + B64.encodeToString(Long.toString(exp).getBytes(StandardCharsets.UTF_8))
        + "."
        + mac;
  }

  public boolean matches(IdentityUser user, String code) {
    if (user == null || code == null || code.isBlank()) {
      return false;
    }
    String[] parts = code.split("\\.", 3);
    if (parts.length != 3) {
      return false;
    }
    String userId;
    long exp;
    try {
      userId = new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
      exp = Long.parseLong(new String(B64D.decode(parts[1]), StandardCharsets.UTF_8));
    } catch (RuntimeException ex) {
      return false;
    }
    if (!user.id().equals(userId) || Instant.ofEpochMilli(exp).isBefore(clock.instant())) {
      return false;
    }
    String expected = sign(userId, exp, stamp(user));
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII));
  }

  private static String stamp(IdentityUser user) {
    return user.securityStamp() == null ? "" : user.securityStamp();
  }

  private String sign(String userId, long exp, String stamp) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] raw =
          mac.doFinal((userId + "\n" + exp + "\n" + stamp).getBytes(StandardCharsets.UTF_8));
      return B64.encodeToString(raw);
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
