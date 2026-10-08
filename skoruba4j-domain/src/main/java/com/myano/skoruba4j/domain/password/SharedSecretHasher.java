package com.myano.skoruba4j.domain.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** IdentityServer4 SharedSecret storage: SHA-256 / SHA-512 of UTF-8, then Base64. */
public final class SharedSecretHasher {
  private SharedSecretHasher() {}

  public static String sha256Base64(String secret) {
    return hashBase64(secret, "SHA-256");
  }

  public static String sha512Base64(String secret) {
    return hashBase64(secret, "SHA-512");
  }

  /**
   * @param hashType Skoruba/IS4 UI values {@code Sha256} / {@code Sha512} (case-insensitive)
   */
  public static String hashForType(String secret, String hashType) {
    if (hashType != null && hashType.trim().equalsIgnoreCase("Sha512")) {
      return sha512Base64(secret);
    }
    return sha256Base64(secret);
  }

  private static String hashBase64(String secret, String algorithm) {
    if (secret == null) {
      throw new IllegalArgumentException("secret is required");
    }
    try {
      byte[] digest =
          MessageDigest.getInstance(algorithm).digest(secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
