package com.myano.skoruba4j.domain.password;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SharedSecretHasherTest {

  @Test
  void matchesIdentityServer4SharedSecret() throws Exception {
    String secret = "plain-secret";
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
    assertEquals(Base64.getEncoder().encodeToString(digest), SharedSecretHasher.sha256Base64(secret));
  }

  @Test
  void hashForTypeSupportsSha512() throws Exception {
    String secret = "plain-secret";
    byte[] digest = MessageDigest.getInstance("SHA-512").digest(secret.getBytes(StandardCharsets.UTF_8));
    assertEquals(
        Base64.getEncoder().encodeToString(digest), SharedSecretHasher.hashForType(secret, "Sha512"));
    assertEquals(SharedSecretHasher.sha256Base64(secret), SharedSecretHasher.hashForType(secret, "Sha256"));
  }
}
