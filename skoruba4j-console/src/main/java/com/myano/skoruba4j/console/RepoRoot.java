package com.myano.skoruba4j.console;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Install home: packaged {@code dist/} in this repo, or the copied install directory. */
public final class RepoRoot {
  private RepoRoot() {}

  public static Path find() {
    String env = System.getenv("IDSERVER_HOME");
    if (env != null && !env.isBlank()) {
      return Path.of(env.trim()).toAbsolutePath().normalize();
    }
    Path installed = fromConsoleJar();
    if (installed != null) {
      return installed;
    }
    Path dir = Path.of("").toAbsolutePath();
    for (int i = 0; i < 6 && dir != null; i++) {
      if (isInstallHome(dir)) {
        return dir;
      }
      Path pom = dir.resolve("pom.xml");
      try {
        if (Files.exists(pom) && isParentPom(Files.readString(pom))) {
          Path packaged = dir.resolve("dist");
          if (isInstallHome(packaged)) {
            return packaged;
          }
          return dir;
        }
      } catch (Exception ignored) {
        return dir;
      }
      dir = dir.getParent();
    }
    return Path.of("").toAbsolutePath();
  }

  /** True for {@code dist/} after {@code mvn package}, and for the installer copy of that tree. */
  public static boolean isInstallHome(Path home) {
    if (home == null) {
      return false;
    }
    return Files.isDirectory(home.resolve("lib"))
        && Files.isDirectory(home.resolve("jdk"))
        && Files.isRegularFile(home.resolve("jdk").resolve("bin").resolve("java.exe"));
  }

  static Path fromConsoleJar() {
    try {
      var source = RepoRoot.class.getProtectionDomain().getCodeSource();
      if (source == null || source.getLocation() == null) {
        return null;
      }
      Path jar = Paths.get(URI.create(source.getLocation().toString()));
      if (!Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
        return null;
      }
      Path lib = jar.getParent();
      if (lib == null || !"lib".equalsIgnoreCase(lib.getFileName().toString())) {
        return null;
      }
      Path home = lib.getParent();
      return isInstallHome(home) ? home : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  static boolean isParentPom(String pomXml) {
    if (pomXml == null) {
      return false;
    }
    return pomXml.contains("<artifactId>skoruba4j-parent</artifactId>")
        && pomXml.contains("<packaging>pom</packaging>");
  }
}
