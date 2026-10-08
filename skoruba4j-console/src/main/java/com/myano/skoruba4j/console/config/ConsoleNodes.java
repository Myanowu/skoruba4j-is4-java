package com.myano.skoruba4j.console.config;

import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Default local install nodes. Remote: set local=false and change healthUrl. */
public final class ConsoleNodes {
  private ConsoleNodes() {}

    public static Map<String, ConsoleProperties.Node> defaults() {
      return defaults(null);
    }

    public static Map<String, ConsoleProperties.Node> defaults(Path repoRoot) {
      LocalConfigFile.Form form = new LocalConfigFile.Form();
      if (repoRoot != null) {
        try {
          String overlay = LocalConfigFile.read(LocalConfigFile.resolve(repoRoot));
          String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
          form = LocalConfigFile.load(overlay, privateYaml);
        } catch (Exception ignored) {
          form = new LocalConfigFile.Form();
        }
      }
      Map<String, ConsoleProperties.Node> nodes = new LinkedHashMap<>();
      nodes.put("sts", node("STS", form.sts.healthUrl("localhost"), true, "skoruba4j-sts"));
      nodes.put("admin", node("Admin", form.adminProc.healthUrl("localhost"), true, "skoruba4j-admin"));
      nodes.put(
          "admin-api",
          node("Admin API", form.adminApi.healthUrl("localhost"), true, "skoruba4j-admin-api"));
      return nodes;
    }

  static ConsoleProperties.Node node(
      String displayName, String healthUrl, boolean local, String module) {
    ConsoleProperties.Node node = new ConsoleProperties.Node();
    node.setDisplayName(displayName);
    node.setHealthUrl(healthUrl);
    node.setLocal(local);
    node.setModule(module);
    return node;
  }
}
