package com.myano.skoruba4j.console.ui;

import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import java.awt.Component;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JOptionPane;

/** Opens process entry URLs in the system browser. */
final class BrowserLaunch {
  private BrowserLaunch() {}

  static String entryUrl(String healthUrl, String module) {
    return entryUrl(healthUrl, module, true);
  }

  static String entryUrl(String healthUrl, String module, boolean adminApiUiEnabled) {
    String base = origin(healthUrl);
    if (base.isBlank()) {
      return "";
    }
    String mod = module == null ? "" : module.toLowerCase(Locale.ROOT);
    if (mod.contains("admin-api")) {
      // Land on login (same as STS/Admin). Bare "/" needs a session and is easy to confuse.
      return adminApiUiEnabled ? base + "/login" : base + "/health";
    }
    if (mod.contains("admin")) {
      return base + "/login";
    }
    if (mod.contains("sts")) {
      return base + "/login";
    }
    return base + "/";
  }

  static String origin(String healthUrl) {
    if (healthUrl == null || healthUrl.isBlank()) {
      return "";
    }
    String url = healthUrl.trim();
    int q = url.indexOf('?');
    if (q >= 0) {
      url = url.substring(0, q);
    }
    if (url.toLowerCase(Locale.ROOT).endsWith("/health")) {
      url = url.substring(0, url.length() - "/health".length());
    }
    while (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    return url;
  }

  static void open(Component parent, String healthUrl, String module) {
    open(parent, healthUrl, module, null);
  }

  static void open(Component parent, String healthUrl, String module, Path repoRoot) {
    boolean ui = true;
    String mod = module == null ? "" : module.toLowerCase(Locale.ROOT);
    if (mod.contains("admin-api") && repoRoot != null) {
      try {
        String overlay = LocalConfigFile.read(LocalConfigFile.resolve(repoRoot));
        String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
        ui = LocalConfigFile.load(overlay, privateYaml).adminApiUiEnabled;
      } catch (Exception ignored) {
        ui = true;
      }
    }
    String target = entryUrl(healthUrl, module, ui);
    if (target.isBlank()) {
      JOptionPane.showMessageDialog(
          parent, "No browse URL for this node.", "Open in browser", JOptionPane.WARNING_MESSAGE);
      return;
    }
    if (mod.contains("admin-api") && !ui) {
      JOptionPane.showMessageDialog(
          parent,
          "Admin API browser UI is disabled (Settings → Admin API → Enable browser UI = No).\nOpening /health instead.",
          "Open in browser",
          JOptionPane.INFORMATION_MESSAGE);
    }
    try {
      browse(target);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          parent,
          (e.getMessage() == null ? e.toString() : e.getMessage()) + "\n" + target,
          "Open in browser",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Prefer OS shell openers. Bundled JREs on Windows often report {@link Desktop.Action#BROWSE}
   * supported but never open a window.
   */
  static void browse(String url) throws Exception {
    URI uri = URI.create(url);
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    List<Exception> errors = new ArrayList<>();
    if (os.contains("win")) {
      try {
        new ProcessBuilder("cmd", "/c", "start", "", url).start();
        return;
      } catch (Exception e) {
        errors.add(e);
      }
      try {
        new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
        return;
      } catch (Exception e) {
        errors.add(e);
      }
    } else if (os.contains("mac")) {
      try {
        new ProcessBuilder("open", url).start();
        return;
      } catch (Exception e) {
        errors.add(e);
      }
    } else {
      try {
        new ProcessBuilder("xdg-open", url).start();
        return;
      } catch (Exception e) {
        errors.add(e);
      }
    }
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      Desktop.getDesktop().browse(uri);
      return;
    }
    Exception last = errors.isEmpty() ? new IllegalStateException("No browser launcher") : errors.get(errors.size() - 1);
    throw last;
  }
}
