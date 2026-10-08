package com.myano.skoruba4j.protocol;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RSA signing key for JWKS / JWT {@code kid}. Mirrors IS4 {@code AddDeveloperSigningCredential}
 * ({@code tempkey.jwk}): load from disk when present, otherwise generate and persist.
 */
public final class SigningRsaKey {
  private static final Logger log = LoggerFactory.getLogger(SigningRsaKey.class);

  private SigningRsaKey() {}

  /** Generate an ephemeral key (tests only). Prefer {@link #loadOrCreate(Path)}. */
  public static RSAKey generate() {
    return newKey(UUID.randomUUID().toString());
  }

  /**
   * Load JWK JSON from {@code file}, or create and write one (C# {@code tempkey.jwk} behaviour).
   * Blank/null path → ephemeral generate (no persistence).
   */
  public static RSAKey loadOrCreate(Path file) {
    if (file == null) {
      return generate();
    }
    try {
      if (Files.isRegularFile(file)) {
        String json = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (!json.isEmpty()) {
          JWK jwk = JWK.parse(json);
          if (!(jwk instanceof RSAKey rsa) || !rsa.isPrivate()) {
            throw new IllegalStateException(
                "Signing key file must be an RSA JWK with private key: " + file.toAbsolutePath());
          }
          RSAKey withMeta = ensureMeta(rsa);
          log.info("Loaded STS signing key kid={} from {}", withMeta.getKeyID(), file.toAbsolutePath());
          return withMeta;
        }
      }
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      RSAKey created = newKey(UUID.randomUUID().toString());
      Files.writeString(file, created.toJSONString(), StandardCharsets.UTF_8);
      log.info("Created STS signing key kid={} at {}", created.getKeyID(), file.toAbsolutePath());
      return created;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load/create signing key at " + file, e);
    }
  }

  private static RSAKey ensureMeta(RSAKey rsa) throws Exception {
    RSAKey.Builder builder = new RSAKey.Builder(rsa);
    if (rsa.getKeyID() == null || rsa.getKeyID().isBlank()) {
      builder.keyID(UUID.randomUUID().toString());
    }
    if (rsa.getKeyUse() == null) {
      builder.keyUse(KeyUse.SIGNATURE);
    }
    if (rsa.getAlgorithm() == null) {
      builder.algorithm(JWSAlgorithm.RS256);
    }
    return builder.build();
  }

  private static RSAKey newKey(String kid) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
          .privateKey((RSAPrivateKey) keyPair.getPrivate())
          .keyID(kid)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to generate JWK", e);
    }
  }
}
