package com.myano.skoruba4j.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AES-GCM helper for the STS auth cookie key ring (C# Data Protection analogue — Java keys only,
 * not ASP.NET {@code DataProtectionKeys} XML).
 */
public final class AuthCookieCipher {
  private static final Logger log = LoggerFactory.getLogger(AuthCookieCipher.class);
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private final SecretKey key;

  public AuthCookieCipher(SecretKey key) {
    this.key = key;
  }

  public static AuthCookieCipher loadOrCreate(Path keyFile) {
    if (keyFile == null) {
      throw new IllegalArgumentException("auth cookie key file is required");
    }
    try {
      if (Files.isRegularFile(keyFile) && Files.size(keyFile) >= 32) {
        byte[] raw = Files.readAllBytes(keyFile);
        byte[] keyBytes = new byte[32];
        System.arraycopy(raw, 0, keyBytes, 0, 32);
        log.info("Loaded STS auth-cookie key from {}", keyFile.toAbsolutePath());
        return new AuthCookieCipher(new SecretKeySpec(keyBytes, "AES"));
      }
      Path parent = keyFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(256);
      SecretKey created = generator.generateKey();
      Files.write(keyFile, created.getEncoded());
      log.info("Created STS auth-cookie key at {}", keyFile.toAbsolutePath());
      return new AuthCookieCipher(created);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load/create auth cookie key at " + keyFile, e);
    }
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
      buffer.put(iv);
      buffer.put(cipherText);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    } catch (Exception e) {
      throw new IllegalStateException("auth cookie encrypt failed", e);
    }
  }

  public String decrypt(String token) {
    try {
      byte[] all = Base64.getUrlDecoder().decode(token);
      if (all.length <= IV_BYTES) {
        throw new IllegalArgumentException("token too short");
      }
      byte[] iv = new byte[IV_BYTES];
      byte[] cipherText = new byte[all.length - IV_BYTES];
      System.arraycopy(all, 0, iv, 0, IV_BYTES);
      System.arraycopy(all, IV_BYTES, cipherText, 0, cipherText.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException("auth cookie decrypt failed", e);
    }
  }
}
