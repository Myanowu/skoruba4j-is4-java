package com.myano.skoruba4j.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoRootTest {

  @Test
  void parentPomIsPackagingPomNotAModule() {
    assertTrue(
        RepoRoot.isParentPom(
            "<artifactId>skoruba4j-parent</artifactId><packaging>pom</packaging>"));
    assertFalse(
        RepoRoot.isParentPom(
            "<parent><artifactId>skoruba4j-parent</artifactId></parent><artifactId>skoruba4j-console</artifactId>"));
  }

  @Test
  void packagedDistIsTheInstallHome(@TempDir Path repo) throws Exception {
    Path dist = repo.resolve("dist");
    Files.createDirectories(dist.resolve("lib"));
    Files.createDirectories(dist.resolve("jdk").resolve("bin"));
    Files.writeString(dist.resolve("jdk").resolve("bin").resolve("java.exe"), "x");
    Files.createDirectories(dist.resolve("data"));
    Files.createDirectories(dist.resolve("config"));
    assertTrue(RepoRoot.isInstallHome(dist));
    assertFalse(RepoRoot.isInstallHome(repo));
  }
}
