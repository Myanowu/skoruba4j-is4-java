package com.myano.skoruba4j.domain.password;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * ASP.NET Identity {@code PasswordHasher} layout: Base64 of a versioned PBKDF2 payload (not MCF).
 * New hashes are Identity V3 (HMAC-SHA256, 100_000 iterations).
 */
public final class IdentityPasswordHasher {
  public static final int V3_ITERATIONS = 100_000;
  private static final int SALT_SIZE = 16;
  private static final int SUBKEY_SIZE = 32;
  private static final int V2_ITERATIONS = 1_000;
  private static final byte FORMAT_V2 = 0x00;
  private static final byte FORMAT_V3 = 0x01;
  private static final int PRF_SHA1 = 0;
  private static final int PRF_SHA256 = 1;
  private static final int PRF_SHA512 = 2;

  private final SecureRandom random;

  public IdentityPasswordHasher() {
    this(new SecureRandom());
  }

  IdentityPasswordHasher(SecureRandom random) {
    this.random = random;
  }

  public String hash(String password) {
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("password is required");
    }
    byte[] salt = new byte[SALT_SIZE];
    random.nextBytes(salt);
    byte[] subkey = pbkdf2(password, salt, "PBKDF2WithHmacSHA256", V3_ITERATIONS, SUBKEY_SIZE);
    ByteBuffer buf = ByteBuffer.allocate(13 + SALT_SIZE + SUBKEY_SIZE);
    buf.put(FORMAT_V3);
    buf.putInt(PRF_SHA256);
    buf.putInt(V3_ITERATIONS);
    buf.putInt(SALT_SIZE);
    buf.put(salt);
    buf.put(subkey);
    return Base64.getEncoder().encodeToString(buf.array());
  }

  public PasswordVerificationResult verify(String hashedPassword, String password) {
    if (hashedPassword == null || hashedPassword.isBlank() || password == null) {
      return PasswordVerificationResult.FAILED;
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(hashedPassword.trim());
    } catch (IllegalArgumentException e) {
      return PasswordVerificationResult.FAILED;
    }
    if (decoded.length < 1) {
      return PasswordVerificationResult.FAILED;
    }
    return switch (decoded[0]) {
      case FORMAT_V2 -> verifyV2(decoded, password);
      case FORMAT_V3 -> verifyV3(decoded, password);
      default -> PasswordVerificationResult.FAILED;
    };
  }

  private static PasswordVerificationResult verifyV2(byte[] decoded, String password) {
    if (decoded.length != 1 + SALT_SIZE + SUBKEY_SIZE) {
      return PasswordVerificationResult.FAILED;
    }
    byte[] salt = slice(decoded, 1, SALT_SIZE);
    byte[] expected = slice(decoded, 1 + SALT_SIZE, SUBKEY_SIZE);
    byte[] actual = pbkdf2(password, salt, "PBKDF2WithHmacSHA1", V2_ITERATIONS, SUBKEY_SIZE);
    if (!MessageDigest.isEqual(expected, actual)) {
      return PasswordVerificationResult.FAILED;
    }
    return PasswordVerificationResult.SUCCESS_REHASH_NEEDED;
  }

  private static PasswordVerificationResult verifyV3(byte[] decoded, String password) {
    if (decoded.length < 13 + SALT_SIZE) {
      return PasswordVerificationResult.FAILED;
    }
    ByteBuffer buf = ByteBuffer.wrap(decoded);
    buf.get();
    int prf = buf.getInt();
    int iterCount = buf.getInt();
    int saltSize = buf.getInt();
    if (iterCount < 1 || saltSize < SALT_SIZE) {
      return PasswordVerificationResult.FAILED;
    }
    if (decoded.length < 13 + saltSize + 16) {
      return PasswordVerificationResult.FAILED;
    }
    byte[] salt = new byte[saltSize];
    buf.get(salt);
    byte[] expected = new byte[decoded.length - 13 - saltSize];
    buf.get(expected);
    String algorithm = prfAlgorithm(prf);
    if (algorithm == null) {
      return PasswordVerificationResult.FAILED;
    }
    byte[] actual = pbkdf2(password, salt, algorithm, iterCount, expected.length);
    if (!MessageDigest.isEqual(expected, actual)) {
      return PasswordVerificationResult.FAILED;
    }
    if (prf != PRF_SHA256 || iterCount < V3_ITERATIONS || saltSize != SALT_SIZE) {
      return PasswordVerificationResult.SUCCESS_REHASH_NEEDED;
    }
    return PasswordVerificationResult.SUCCESS;
  }

  private static String prfAlgorithm(int prf) {
    return switch (prf) {
      case PRF_SHA1 -> "PBKDF2WithHmacSHA1";
      case PRF_SHA256 -> "PBKDF2WithHmacSHA256";
      case PRF_SHA512 -> "PBKDF2WithHmacSHA512";
      default -> null;
    };
  }

  private static byte[] pbkdf2(
      String password, byte[] salt, String algorithm, int iterations, int keyLength) {
    PBEKeySpec spec =
        new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength * 8);
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
      return factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("PBKDF2 unavailable: " + algorithm, e);
    } finally {
      spec.clearPassword();
    }
  }

  private static byte[] slice(byte[] src, int offset, int length) {
    byte[] out = new byte[length];
    System.arraycopy(src, offset, out, 0, length);
    return out;
  }
}
