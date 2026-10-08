package com.myano.skoruba4j.protocol;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolve {@code IDSERVER_HOME} like skoruba4j-domain SqlitePaths. */
public final class IdServerHome {
  private IdServerHome() {}

  public static Path root() {
    String home = System.getProperty("IDSERVER_HOME");
    if (home == null || home.isBlank()) {
      home = System.getenv("IDSERVER_HOME");
    }
    if (home == null || home.isBlank()) {
      home = System.getProperty("user.dir", ".");
    }
    return Paths.get(home).toAbsolutePath().normalize();
  }

  public static Path resolve(String relativeOrAbsolute) {
    if (relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) {
      return null;
    }
    String raw =
        relativeOrAbsolute
            .trim()
            .replace("${IDSERVER_HOME}", root().toString())
            .replace('/', java.io.File.separatorChar);
    Path path = Paths.get(raw);
    if (!path.isAbsolute()) {
      path = root().resolve(path);
    }
    return path.toAbsolutePath().normalize();
  }
}
