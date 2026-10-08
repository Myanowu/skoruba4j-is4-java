package com.myano.skoruba4j.console.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConsoleProperties {
  private Map<String, Node> nodes = new LinkedHashMap<>();

  public Map<String, Node> getNodes() {
    return nodes;
  }

  public void setNodes(Map<String, Node> nodes) {
    this.nodes = nodes == null ? new LinkedHashMap<>() : nodes;
  }

  public static class Node {
    private String displayName = "";
    private String healthUrl = "";
    private boolean local = true;
    private String module = "";

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getHealthUrl() {
      return healthUrl;
    }

    public void setHealthUrl(String healthUrl) {
      this.healthUrl = healthUrl;
    }

    public boolean isLocal() {
      return local;
    }

    public void setLocal(boolean local) {
      this.local = local;
    }

    public String getModule() {
      return module;
    }

    public void setModule(String module) {
      this.module = module;
    }
  }
}
