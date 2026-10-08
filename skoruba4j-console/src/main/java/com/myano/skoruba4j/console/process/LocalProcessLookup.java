package com.myano.skoruba4j.console.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds local Java processes for a console node (port listen or command line). */
public final class LocalProcessLookup {
  private LocalProcessLookup() {}

  public static int portOf(String healthUrl) {
    if (healthUrl == null || healthUrl.isBlank()) {
      return -1;
    }
    try {
      URI uri = URI.create(healthUrl.trim());
      int port = uri.getPort();
      if (port > 0) {
        return port;
      }
      if ("https".equalsIgnoreCase(uri.getScheme())) {
        return 443;
      }
      if ("http".equalsIgnoreCase(uri.getScheme())) {
        return 80;
      }
    } catch (IllegalArgumentException ignored) {
      return -1;
    }
    return -1;
  }

  public static boolean commandLineMatches(String commandLine, String module) {
    if (commandLine == null || module == null || module.isBlank()) {
      return false;
    }
    String cmd = commandLine.replace('\\', '/').toLowerCase(Locale.ROOT);
    String mod = module.toLowerCase(Locale.ROOT);
    if ("skoruba4j-admin".equals(mod) && cmd.contains("skoruba4j-admin-api")) {
      return false;
    }
    if (cmd.contains(mod + "-") && cmd.contains(".jar")) {
      return true;
    }
    return switch (mod) {
      case "skoruba4j-sts" -> cmd.contains("stsapplication");
      case "skoruba4j-admin" -> cmd.contains("adminapplication") && !cmd.contains("adminapi");
      case "skoruba4j-admin-api" -> cmd.contains("adminapiapplication");
      default -> false;
    };
  }

  public static Set<Long> parseNetstatListeningPids(String netstat, int port) {
    Set<Long> pids = new LinkedHashSet<>();
    if (netstat == null || port <= 0) {
      return pids;
    }
    Pattern line = Pattern.compile("(?i)\\blistening\\b");
    String needle = ":" + port;
    for (String raw : netstat.split("\r?\n")) {
      String row = raw.trim();
      if (!line.matcher(row).find()) {
        continue;
      }
      if (!hasLocalPort(row, needle)) {
        continue;
      }
      Matcher pid = Pattern.compile("(\\d+)\\s*$").matcher(row);
      if (pid.find()) {
        long value = Long.parseLong(pid.group(1));
        if (value > 4) {
          pids.add(value);
        }
      }
    }
    return pids;
  }

  static boolean hasLocalPort(String row, String needle) {
    int idx = row.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    if (idx < 0) {
      return false;
    }
    int after = idx + needle.length();
    if (after < row.length()) {
      char next = row.charAt(after);
      if (Character.isDigit(next)) {
        return false;
      }
    }
    return true;
  }

  public static String netstatOutput() throws Exception {
    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    ProcessBuilder builder =
        windows
            ? new ProcessBuilder("netstat", "-ano", "-p", "tcp")
            : new ProcessBuilder("sh", "-c", "ss -lntp 2>/dev/null || netstat -lntp 2>/dev/null");
    builder.redirectErrorStream(true);
    Process process = builder.start();
    StringBuilder out = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        out.append(line).append('\n');
      }
    }
    process.waitFor();
    return out.toString();
  }
}
