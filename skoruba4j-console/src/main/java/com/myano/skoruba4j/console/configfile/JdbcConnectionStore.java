package com.myano.skoruba4j.console.configfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Named JDBC profiles for Skoruba4j Control. Stored in {@code idserver-connections.yml};
 * the active profile is projected into {@code idserver-local.yml} {@code idserver.db.*}.
 */
public final class JdbcConnectionStore {
  public static final String FILE_NAME = "idserver-connections.yml";

  private JdbcConnectionStore() {}

  public static final class Connection {
    public String id = "";
    public String name = "";
    public String provider = LocalConfigFile.DEFAULT_PROVIDER;
    public String url = "";
    public String username = "";
    public String password = "";
    public String tableStyle = LocalConfigFile.DEFAULT_TABLE_STYLE;

    public Connection copy() {
      Connection c = new Connection();
      c.id = id;
      c.name = name;
      c.provider = provider;
      c.url = url;
      c.username = username;
      c.password = password;
      c.tableStyle = tableStyle;
      return c;
    }
  }

  public static final class Store {
    public int version = 1;
    public String activeId = "";
    public final List<Connection> connections = new ArrayList<>();

    public Connection find(String id) {
      if (id == null || id.isBlank()) {
        return null;
      }
      for (Connection c : connections) {
        if (id.equals(c.id)) {
          return c;
        }
      }
      return null;
    }

    public Connection active() {
      Connection hit = find(activeId);
      if (hit != null) {
        return hit;
      }
      return connections.isEmpty() ? null : connections.get(0);
    }
  }

  public static Path resolve(Path repoRoot) {
    Path local = LocalConfigFile.resolve(repoRoot);
    Path parent = local.getParent();
    if (parent != null) {
      return parent.resolve(FILE_NAME);
    }
    if (repoRoot != null) {
      return repoRoot.resolve("config").resolve(FILE_NAME);
    }
    return Path.of("config").resolve(FILE_NAME).toAbsolutePath().normalize();
  }

  public static String newId() {
    return "conn-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  public static Store loadOrSeed(Path repoRoot) throws IOException {
    Path file = resolve(repoRoot);
    if (Files.exists(file)) {
      Store store = parse(Files.readString(file, StandardCharsets.UTF_8));
      if (!store.connections.isEmpty()) {
        ensureActive(store);
        return store;
      }
    }
    return seedFromLocal(repoRoot);
  }

  public static Store seedFromLocal(Path repoRoot) throws IOException {
    String overlay = LocalConfigFile.read(LocalConfigFile.resolve(repoRoot));
    String privateYaml = LocalConfigFile.read(LocalConfigFile.localProfileFile(repoRoot));
    LocalConfigFile.Form form = LocalConfigFile.load(overlay, privateYaml);
    Connection c = fromForm(form, "local-sqlite", "Local SQLite");
    Store store = new Store();
    store.connections.add(c);
    store.activeId = c.id;
    return store;
  }

  public static Connection fromForm(LocalConfigFile.Form form, String id, String name) {
    Connection c = new Connection();
    c.id = id == null || id.isBlank() ? newId() : id;
    c.name = name == null || name.isBlank() ? "Connection" : name.trim();
    c.provider = LocalConfigFile.canonicalizeProvider(form == null ? null : form.provider);
    c.url = form == null || form.url == null ? "" : form.url;
    if ("sqlite".equals(c.provider) && (c.url == null || c.url.isBlank())) {
      c.url = LocalConfigFile.EMBEDDED_JDBC_URL;
    }
    c.username = form == null || form.username == null ? "" : form.username;
    c.password = form == null || form.password == null ? "" : form.password;
    c.tableStyle =
        LocalConfigFile.orDefault(
            form == null ? null : form.tableStyle, LocalConfigFile.DEFAULT_TABLE_STYLE);
    return c;
  }

  public static void applyToForm(Connection c, LocalConfigFile.Form form) {
    if (form == null || c == null) {
      return;
    }
    form.provider = LocalConfigFile.canonicalizeProvider(c.provider);
    form.url = c.url == null ? "" : c.url;
    form.username = c.username == null ? "" : c.username;
    form.password = c.password == null ? "" : c.password;
    form.tableStyle =
        LocalConfigFile.orDefault(c.tableStyle, LocalConfigFile.DEFAULT_TABLE_STYLE);
  }

  public static void save(Path file, Store store) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("connections file path required");
    }
    ensureActive(store);
    Files.createDirectories(file.getParent());
    Files.writeString(file, write(store), StandardCharsets.UTF_8);
  }

  public static Store parse(String yaml) {
    Store store = new Store();
    if (yaml == null || yaml.isBlank()) {
      return store;
    }
    store.version = LocalConfigFile.parseInt(LocalConfigFile.yamlValue(yaml, "version"), 1);
    store.activeId = LocalConfigFile.yamlValue(yaml, "active-id");
    List<Map<String, String>> blocks = parseConnectionBlocks(yaml);
    for (Map<String, String> block : blocks) {
      Connection c = new Connection();
      c.id = block.getOrDefault("id", "");
      c.name = block.getOrDefault("name", "");
      c.provider = LocalConfigFile.canonicalizeProvider(block.get("provider"));
      c.url = block.getOrDefault("url", "");
      c.username = block.getOrDefault("username", "");
      c.password = block.getOrDefault("password", "");
      c.tableStyle =
          LocalConfigFile.orDefault(block.get("table-style"), LocalConfigFile.DEFAULT_TABLE_STYLE);
      if (c.id.isBlank()) {
        c.id = newId();
      }
      if (c.name.isBlank()) {
        c.name = c.id;
      }
      store.connections.add(c);
    }
    ensureActive(store);
    return store;
  }

  public static String write(Store store) {
    Store s = store == null ? new Store() : store;
    ensureActive(s);
    StringBuilder yaml = new StringBuilder();
    yaml.append("# Written by skoruba4j-console. Gitignored. Do not commit.\n");
    yaml.append("version: ").append(s.version <= 0 ? 1 : s.version).append('\n');
    yaml.append("active-id: ").append(LocalConfigFile.quoted(s.activeId)).append('\n');
    yaml.append("connections:\n");
    if (s.connections.isEmpty()) {
      yaml.append("  []\n");
      return yaml.toString();
    }
    for (Connection c : s.connections) {
      yaml.append("  - id: ").append(LocalConfigFile.quoted(c.id)).append('\n');
      yaml.append("    name: ").append(LocalConfigFile.quoted(nullToEmpty(c.name))).append('\n');
      yaml.append("    provider: ")
          .append(LocalConfigFile.canonicalizeProvider(c.provider))
          .append('\n');
      yaml.append("    url: ").append(LocalConfigFile.quoted(nullToEmpty(c.url))).append('\n');
      yaml.append("    username: ")
          .append(LocalConfigFile.quoted(nullToEmpty(c.username)))
          .append('\n');
      yaml.append("    password: ")
          .append(LocalConfigFile.quoted(nullToEmpty(c.password)))
          .append('\n');
      yaml.append("    table-style: ")
          .append(
              LocalConfigFile.orDefault(c.tableStyle, LocalConfigFile.DEFAULT_TABLE_STYLE))
          .append('\n');
    }
    return yaml.toString();
  }

  public static String uniqueName(Store store, String base) {
    String root = base == null || base.isBlank() ? "Connection" : base.trim();
    if (store == null || store.connections.isEmpty()) {
      return root;
    }
    if (!nameTaken(store, root, null)) {
      return root;
    }
    for (int i = 2; i < 1000; i++) {
      String candidate = root + " (" + i + ")";
      if (!nameTaken(store, candidate, null)) {
        return candidate;
      }
    }
    return root + " " + newId();
  }

  public static boolean nameTaken(Store store, String name, String exceptId) {
    if (store == null || name == null) {
      return false;
    }
    String n = name.trim().toLowerCase(Locale.ROOT);
    for (Connection c : store.connections) {
      if (exceptId != null && exceptId.equals(c.id)) {
        continue;
      }
      if (c.name != null && c.name.trim().toLowerCase(Locale.ROOT).equals(n)) {
        return true;
      }
    }
    return false;
  }

  public static void ensureActive(Store store) {
    if (store == null || store.connections.isEmpty()) {
      if (store != null) {
        store.activeId = "";
      }
      return;
    }
    if (store.find(store.activeId) == null) {
      store.activeId = store.connections.get(0).id;
    }
  }

  private static List<Map<String, String>> parseConnectionBlocks(String yaml) {
    List<Map<String, String>> blocks = new ArrayList<>();
    boolean inConnections = false;
    int connectionsIndent = -1;
    Map<String, String> current = null;
    int itemIndent = -1;
    for (String line : yaml.split("\n", -1)) {
      if (line.trim().isEmpty() || line.trim().startsWith("#")) {
        continue;
      }
      int indent = 0;
      while (indent < line.length() && line.charAt(indent) == ' ') {
        indent++;
      }
      String trimmed = line.trim();
      if (!inConnections) {
        if (trimmed.equals("connections:") || trimmed.startsWith("connections:")) {
          inConnections = true;
          connectionsIndent = indent;
        }
        continue;
      }
      if (indent <= connectionsIndent && !trimmed.startsWith("-")) {
        break;
      }
      if (trimmed.startsWith("- ")) {
        if (current != null) {
          blocks.add(current);
        }
        current = new LinkedHashMap<>();
        itemIndent = indent;
        String rest = trimmed.substring(2).trim();
        putPair(current, rest);
        continue;
      }
      if (current != null && indent > itemIndent) {
        putPair(current, trimmed);
      }
    }
    if (current != null) {
      blocks.add(current);
    }
    return blocks;
  }

  private static void putPair(Map<String, String> map, String trimmed) {
    int colon = trimmed.indexOf(':');
    if (colon <= 0) {
      return;
    }
    String key = trimmed.substring(0, colon).trim();
    String value = trimmed.substring(colon + 1).trim();
    if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
      value = value.substring(1, value.length() - 1).replace("''", "'");
    }
    map.put(key, value);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
