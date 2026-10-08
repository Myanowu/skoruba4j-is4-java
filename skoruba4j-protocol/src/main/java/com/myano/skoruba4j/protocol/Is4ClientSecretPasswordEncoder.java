package com.myano.skoruba4j.protocol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;

/** IS4 SharedSecret: SHA-256 of UTF-8 secret, stored as Base64. Also accepts plaintext. */
public final class Is4ClientSecretPasswordEncoder implements PasswordEncoder {
  @Override
  public String encode(CharSequence rawPassword) {
    return sha256Base64(rawPassword.toString());
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (rawPassword == null || encodedPassword == null) {
      return false;
    }
    String raw = rawPassword.toString();
    if (asciiEqual(sha256Base64(raw), encodedPassword)
        || asciiEqual(sha512Base64(raw), encodedPassword)) {
      return true;
    }
    return MessageDigest.isEqual(
        raw.getBytes(StandardCharsets.UTF_8), encodedPassword.getBytes(StandardCharsets.UTF_8));
  }

  public static String sha256Base64(String secret) {
    return digestBase64(secret, "SHA-256");
  }

  public static String sha512Base64(String secret) {
    return digestBase64(secret, "SHA-512");
  }

  private static boolean asciiEqual(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
  }

  private static String digestBase64(String secret, String algorithm) {
    try {
      byte[] digest =
          MessageDigest.getInstance(algorithm).digest(secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
