package com.myano.skoruba4j.domain.identity;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits) aligned with ASP.NET Identity {@code
 * AuthenticatorTokenProvider} (±2 time-step window).
 */
public final class TotpCodes {
  private static final int STEP_SECONDS = 30;
  private static final int CODE_DIGITS = 6;
  private static final int WINDOW = 2;
  private static final char[] BASE32 =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  private TotpCodes() {}

  /** New Base32 authenticator key (160-bit), same shape ASP.NET Identity generates. */
  public static String generateKey() {
    byte[] raw = new byte[20];
    new SecureRandom().nextBytes(raw);
    return encodeBase32(raw);
  }

  public static boolean verify(String base32Key, String code) {
    return verify(base32Key, code, System.currentTimeMillis() / 1000L);
  }

  static boolean verify(String base32Key, String code, long unixSeconds) {
    if (base32Key == null || base32Key.isBlank() || code == null || code.isBlank()) {
      return false;
    }
    String trimmed = code.trim();
    if (trimmed.length() != CODE_DIGITS || !trimmed.chars().allMatch(Character::isDigit)) {
      return false;
    }
    int expected;
    try {
      expected = Integer.parseInt(trimmed);
    } catch (NumberFormatException e) {
      return false;
    }
    byte[] keyBytes;
    try {
      keyBytes = decodeBase32(base32Key);
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (keyBytes.length == 0) {
      return false;
    }
    long timestep = unixSeconds / STEP_SECONDS;
    for (int i = -WINDOW; i <= WINDOW; i++) {
      if (computeCode(keyBytes, timestep + i) == expected) {
        return true;
      }
    }
    return false;
  }

  static int computeCode(byte[] key, long timestep) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timestep).array());
      int offset = hash[hash.length - 1] & 0x0f;
      int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);
      int mod = 1;
      for (int i = 0; i < CODE_DIGITS; i++) {
        mod *= 10;
      }
      return binary % mod;
    } catch (Exception e) {
      throw new IllegalStateException("TOTP compute failed", e);
    }
  }

  /** otpauth URI for authenticator apps (no QR rendering). */
  public static String otpAuthUri(String issuer, String account, String base32Key) {
    String safeIssuer = encodeUri(issuer == null || issuer.isBlank() ? "Skoruba4j" : issuer);
    String safeAccount = encodeUri(account == null ? "" : account);
    String label = safeIssuer + ":" + safeAccount;
    return "otpauth://totp/"
        + label
        + "?secret="
        + normalizeKey(base32Key)
        + "&issuer="
        + safeIssuer
        + "&digits="
        + CODE_DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  static String normalizeKey(String key) {
    return key == null ? "" : key.trim().replace(" ", "").toUpperCase(Locale.ROOT);
  }

  static String encodeBase32(byte[] data) {
    StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        out.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      out.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
    }
    return out.toString();
  }

  static byte[] decodeBase32(String encoded) {
    String s = normalizeKey(encoded).replace("=", "");
    if (s.isEmpty()) {
      return new byte[0];
    }
    int buffer = 0;
    int bitsLeft = 0;
    byte[] out = new byte[s.length() * 5 / 8];
    int index = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      int val = valueOf(c);
      if (val < 0) {
        throw new IllegalArgumentException("invalid base32");
      }
      buffer = (buffer << 5) | val;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    if (index == out.length) {
      return out;
    }
    byte[] trimmed = new byte[index];
    System.arraycopy(out, 0, trimmed, 0, index);
    return trimmed;
  }

  private static int valueOf(char c) {
    if (c >= 'A' && c <= 'Z') {
      return c - 'A';
    }
    if (c >= '2' && c <= '7') {
      return c - '2' + 26;
    }
    return -1;
  }

  private static String encodeUri(String value) {
    StringBuilder out = new StringBuilder();
    for (char c : value.toCharArray()) {
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.'
          || c == '~'
          || c == '@') {
        out.append(c);
      } else {
        out.append('%');
        String hex = Integer.toHexString(c).toUpperCase(Locale.ROOT);
        if (hex.length() == 1) {
          out.append('0');
        }
        out.append(hex);
      }
    }
    return out.toString();
  }
}
