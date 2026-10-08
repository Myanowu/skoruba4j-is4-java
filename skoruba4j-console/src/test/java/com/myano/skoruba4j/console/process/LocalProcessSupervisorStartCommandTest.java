package com.myano.skoruba4j.console.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProcessSupervisorStartCommandTest {

  @Test
  void adminStartDoesNotReplaceJvmTrustStoreWithServerPkcs12(@TempDir Path dir) throws Exception {
    Path p12 = dir.resolve("local-ssl.p12");
    Files.writeString(p12, "placeholder");
    Path jar = dir.resolve("skoruba4j-admin-0.1.0-SNAPSHOT.jar");
    Files.writeString(jar, "placeholder");
    List<String> command = LocalProcessSupervisor.startCommand(dir, jar, "skoruba4j-admin");
    assertFalse(
        command.stream().anyMatch(s -> s.contains("javax.net.ssl.trustStore")),
        String.join(" ", command));
  }

  @Test
  void stsStartDoesNotReplaceJvmTrustStoreEvenWhenSettingsHaveKeyStore(@TempDir Path dir)
      throws Exception {
    Path p12 = dir.resolve("local-ssl.p12");
    Files.writeString(p12, "placeholder");
    Path jar = dir.resolve("skoruba4j-sts-0.1.0-SNAPSHOT.jar");
    Files.writeString(jar, "placeholder");
    List<String> command = LocalProcessSupervisor.startCommand(dir, jar, "skoruba4j-sts", dir);
    assertTrue(
        command.stream().anyMatch(s -> s.startsWith("-Dspring.config.additional-location=")),
        String.join(" ", command));
    assertFalse(
        command.stream().anyMatch(s -> s.contains("javax.net.ssl.trustStore")),
        String.join(" ", command));
  }

  @Test
  void httpsStartPinsTomcatTrustStoreToJdkCacerts(@TempDir Path dir) throws Exception {
    Files.createDirectories(dir.resolve("config"));
    Files.writeString(
        dir.resolve("config").resolve("idserver-local.yml"),
        """
        server:
          ssl:
            enabled: true
            key-store: file:${user.dir}/local-ssl.p12
            key-store-password: changeit
            key-store-type: PKCS12
        """);
    Path jar = dir.resolve("skoruba4j-admin-0.1.0-SNAPSHOT.jar");
    Files.writeString(jar, "placeholder");
    List<String> command = LocalProcessSupervisor.startCommand(dir, jar, "skoruba4j-admin", dir);
    assertTrue(command.stream().anyMatch(s -> s.equals("-Xmx512m")), String.join(" ", command));
    assertTrue(command.stream().anyMatch(s -> s.equals("-Dserver.port=6061")), String.join(" ", command));
    assertTrue(
        command.stream()
            .anyMatch(s -> s.equals("-Didserver.issuer-uri=" + LocalConfigFile.DEFAULT_ISSUER_URI)),
        String.join(" ", command));
    assertTrue(
        command.stream()
            .anyMatch(s -> s.equals("-Didserver.admin.role=" + LocalConfigFile.DEFAULT_ADMIN_ROLE)),
        String.join(" ", command));
    assertTrue(
        command.stream()
            .anyMatch(
                s -> s.equals("-Didserver.admin.login-mode=" + LocalConfigFile.DEFAULT_LOGIN_MODE)),
        String.join(" ", command));
    assertTrue(
        command.stream().anyMatch(s -> s.contains("oss,local,node-admin")),
        String.join(" ", command));
    assertTrue(
        command.stream()
            .anyMatch(s -> s.startsWith("-Dserver.ssl.trust-store=") && s.endsWith("cacerts")),
        String.join(" ", command));
    assertFalse(command.stream().anyMatch(s -> s.contains("javax.net.ssl.trustStore")));
  }

  @Test
  void installTreePrefersLibJarAndTomcatWorkDir(@TempDir Path home) throws Exception {
    Path lib = home.resolve("lib");
    Files.createDirectories(lib);
    Path jar = lib.resolve("skoruba4j-sts-0.1.0-SNAPSHOT.jar");
    Files.writeString(jar, "placeholder");
    assertEquals(jar, LocalProcessSupervisor.findRunnableJar(home, "skoruba4j-sts"));
    assertEquals(home.resolve("tomcat").resolve("skoruba4j-sts"), LocalProcessSupervisor.workDir(home, "skoruba4j-sts"));
  }
}
