package com.myano.skoruba4j.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningRsaKeyTest {

  @Test
  void jwkHasKidUseAndRs256ForIdentityModel() {
    RSAKey key = SigningRsaKey.generate();
    assertNotNull(key.getKeyID());
    assertFalse(key.getKeyID().isBlank());
    assertEquals(KeyUse.SIGNATURE, key.getKeyUse());
    assertEquals("RS256", key.getAlgorithm().getName());
    RSAKey published = key.toPublicJWK();
    assertEquals(key.getKeyID(), published.getKeyID());
    assertEquals(KeyUse.SIGNATURE, published.getKeyUse());
  }

  @Test
  void loadOrCreateReusesSameKid(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("sts-signing.jwk");
    RSAKey first = SigningRsaKey.loadOrCreate(file);
    assertNotNull(Files.readString(file));
    RSAKey second = SigningRsaKey.loadOrCreate(file);
    assertEquals(first.getKeyID(), second.getKeyID());
    assertEquals(first.toJSONString(), second.toJSONString());
  }
}
