package com.myano.skoruba4j.tls;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutboundTrustTest {

  @Test
  void sslContextUsesOsOrJvmTrustWhenLocationBlank() {
    SSLContext context = OutboundTrust.sslContext("", "", "");
    assertNotNull(context);
    assertNotNull(context.getSocketFactory());
  }

  @Test
  void homemadeServerPkcs12BecomesATrustAnchor(@TempDir Path dir) throws Exception {
    Path p12 = dir.resolve("local-ssl.p12");
    createSelfSignedPkcs12(p12, "changeit");
    KeyStore material =
        OutboundTrust.trustMaterial("", "", "", p12.toString(), "changeit", "PKCS12");
    assertTrue(OutboundTrust.hasTrustAnchors(material));
    boolean homemade = false;
    var aliases = material.aliases();
    while (aliases.hasMoreElements()) {
      if (aliases.nextElement().startsWith("https-")) {
        homemade = true;
        break;
      }
    }
    assertTrue(homemade);
    assertNotNull(OutboundTrust.sslContext("", "", "", p12.toString(), "changeit", "PKCS12"));
  }

  private static void createSelfSignedPkcs12(Path p12, String password) throws Exception {
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    if (Files.isRegularFile(Path.of(keytool + ".exe"))) {
      keytool = keytool + ".exe";
    }
    Process process =
        new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias",
                "localhost",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "2",
                "-dname",
                "CN=localhost",
                "-storetype",
                "PKCS12",
                "-keystore",
                p12.toString(),
                "-storepass",
                password,
                "-keypass",
                password)
            .redirectErrorStream(true)
            .start();
    boolean finished = process.waitFor(30, TimeUnit.SECONDS);
    assertTrue(finished && process.exitValue() == 0 && Files.isRegularFile(p12));
  }
}
