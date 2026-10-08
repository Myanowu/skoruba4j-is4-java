package com.myano.skoruba4j.console.jdbc;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse / build JDBC URLs for the four supported providers. YAML still stores one {@code url}
 * string; the Control UI edits host / file / extras and composes this.
 */
public final class JdbcUrlComposer {
  public static final String SQLITE_DEFAULT_EXTRA = "journal_mode=WAL&busy_timeout=8000";
  public static final String SQLSERVER_DEFAULT_EXTRA =
      "encrypt=true;trustServerCertificate=true;loginTimeout=15";

  private static final Pattern SQLSERVER =
      Pattern.compile(
          "^jdbc:sqlserver://([^:;/]+)(?::(\\d+))?(?:;(.*))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern POSTGRES =
      Pattern.compile(
          "^jdbc:postgresql://([^:/?]+)(?::(\\d+))?/([^?]*)(?:\\?(.*))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern MYSQL =
      Pattern.compile(
          "^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]*)(?:\\?(.*))?$", Pattern.CASE_INSENSITIVE);

  private JdbcUrlComposer() {}

  public static final class Parts {
    public String provider = "sqlite";
    public String host = "localhost";
    public String port = "";
    public String database = "";
    public String sqliteFile = "";
    public String extra = "";
    /** True when the URL could not be split into fields (keep raw URL). */
    public boolean rawOnly;
    public String rawUrl = "";
  }

  public static int defaultPort(String provider) {
    return switch (canonicalize(provider)) {
      case "sqlserver" -> 1433;
      case "postgresql" -> 5432;
      case "mysql" -> 3306;
      default -> 0;
    };
  }

  public static String defaultExtra(String provider) {
    return switch (canonicalize(provider)) {
      case "sqlite" -> SQLITE_DEFAULT_EXTRA;
      case "sqlserver" -> SQLSERVER_DEFAULT_EXTRA;
      default -> "";
    };
  }

  public static String driverClass(String provider) {
    return DbProvider.fromConfig(canonicalize(provider)).driverClassName();
  }

  public static Parts parse(String provider, String url, Path installHome) {
    Parts parts = new Parts();
    parts.provider = canonicalize(provider);
    String raw = url == null ? "" : url.trim();
    parts.rawUrl = raw;
    if (raw.isBlank()) {
      parts.port = defaultPort(parts.provider) > 0 ? Integer.toString(defaultPort(parts.provider)) : "";
      parts.extra = defaultExtra(parts.provider);
      if ("sqlite".equals(parts.provider)) {
        parts.sqliteFile = portableSqliteFile();
      }
      return parts;
    }
    try {
      return switch (parts.provider) {
        case "sqlite" -> parseSqlite(raw, installHome);
        case "sqlserver" -> parseSqlServer(raw);
        case "postgresql" -> parsePostgres(raw);
        case "mysql" -> parseMysql(raw);
        default -> rawOnly(parts.provider, raw);
      };
    } catch (RuntimeException ignored) {
      return rawOnly(parts.provider, raw);
    }
  }

  public static String compose(Parts parts, Path installHome) {
    if (parts == null) {
      return "";
    }
    if (parts.rawOnly && parts.rawUrl != null && !parts.rawUrl.isBlank()) {
      String raw = parts.rawUrl.trim();
      if ("sqlserver".equals(canonicalize(parts.provider))) {
        return ensureSqlServerTrust(raw);
      }
      return raw;
    }
    String provider = canonicalize(parts.provider);
    return switch (provider) {
      case "sqlite" -> composeSqlite(parts, installHome);
      case "sqlserver" -> composeSqlServer(parts);
      case "postgresql" -> composePostgres(parts);
      case "mysql" -> composeMysql(parts);
      default -> parts.rawUrl == null ? "" : parts.rawUrl.trim();
    };
  }

  /**
   * Rebuild a JDBC URL for runtime use. Repairs SQL Server URLs that still carry SQLite extras or
   * omit {@code trustServerCertificate} (mssql-jdbc 12 defaults encrypt=true).
   */
  public static String normalize(String provider, String url, Path installHome) {
    String prov = canonicalize(provider);
    String raw = url == null ? "" : url.trim();
    if (raw.isBlank()) {
      return raw;
    }
    if ("sqlserver".equals(prov)) {
      Parts parts = parse(prov, raw, installHome);
      if (parts.rawOnly) {
        return ensureSqlServerTrust(raw);
      }
      if (isStaleExtra(prov, parts.extra)) {
        parts.extra = SQLSERVER_DEFAULT_EXTRA;
      }
      return composeSqlServer(parts);
    }
    if ("sqlite".equals(prov)) {
      return SqlitePaths.resolveJdbcUrl(raw, installHome);
    }
    return raw;
  }

  /** Ensure encrypt + trustServerCertificate on a raw sqlserver JDBC URL. */
  public static String ensureSqlServerTrust(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    String value = url.trim();
    // Strip accidental SQLite query leftovers pasted into sqlserver URLs.
    int junk = indexOfIgnoreCase(value, ";journal_mode=");
    if (junk < 0) {
      junk = indexOfIgnoreCase(value, "?journal_mode=");
    }
    if (junk >= 0) {
      value = value.substring(0, junk);
    }
    LinkedHashMap<String, String> props = new LinkedHashMap<>();
    Matcher m = SQLSERVER.matcher(value);
    if (m.matches()) {
      props.putAll(splitSemi(m.group(3)));
      String host = m.group(1);
      String port = m.group(2) == null ? Integer.toString(defaultPort("sqlserver")) : m.group(2);
      String database = first(props, "databaseName", "database");
      props.remove("databaseName");
      props.remove("database");
      removeIgnoreCase(props, "journal_mode");
      removeIgnoreCase(props, "busy_timeout");
      if (!containsIgnoreCase(props, "encrypt")) {
        props.put("encrypt", "true");
      }
      if (!containsIgnoreCase(props, "trustServerCertificate")) {
        props.put("trustServerCertificate", "true");
      }
      if (!containsIgnoreCase(props, "loginTimeout")) {
        props.put("loginTimeout", "15");
      }
      StringBuilder sb = new StringBuilder("jdbc:sqlserver://").append(host).append(':').append(port);
      LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
      if (database != null && !database.isBlank()) {
        ordered.put("databaseName", database);
      }
      ordered.putAll(props);
      if (!ordered.isEmpty()) {
        sb.append(';').append(joinSemi(ordered));
      }
      return sb.toString();
    }
    if (indexOfIgnoreCase(value, "trustServerCertificate=") < 0) {
      value = value + (value.endsWith(";") ? "" : ";") + "trustServerCertificate=true";
    }
    if (indexOfIgnoreCase(value, "encrypt=") < 0) {
      value = value + ";encrypt=true";
    }
    return value;
  }

  public static String portableSqliteFile() {
    return "${IDSERVER_HOME}/" + SqlitePaths.RELATIVE_FILE;
  }

  public static boolean isPortableSqliteFile(String file) {
    if (file == null || file.isBlank()) {
      return false;
    }
    String normalized = file.replace('\\', '/').trim();
    return normalized.equals(portableSqliteFile())
        || normalized.equals(SqlitePaths.RELATIVE_FILE)
        || normalized.endsWith("/" + SqlitePaths.RELATIVE_FILE);
  }

  private static Parts parseSqlite(String url, Path installHome) {
    Parts parts = new Parts();
    parts.provider = "sqlite";
    if (!url.regionMatches(true, 0, "jdbc:sqlite:", 0, "jdbc:sqlite:".length())) {
      return rawOnly("sqlite", url);
    }
    String rest = url.substring("jdbc:sqlite:".length()).trim();
    String query = "";
    int q = rest.indexOf('?');
    if (q >= 0) {
      query = rest.substring(q + 1);
      rest = rest.substring(0, q);
    }
    parts.extra = query.isBlank() ? SQLITE_DEFAULT_EXTRA : query;
    String homeAbs =
        (installHome == null ? Path.of(".") : installHome).toAbsolutePath().normalize().toString()
            .replace('\\', '/');
    String path = rest.replace('\\', '/');
    if (path.equals(portableSqliteFile())
        || path.equals("${idserver.home}/" + SqlitePaths.RELATIVE_FILE)) {
      parts.sqliteFile = portableSqliteFile();
    } else if (path.equals(homeAbs + "/" + SqlitePaths.RELATIVE_FILE)
        || path.equalsIgnoreCase(homeAbs + "/" + SqlitePaths.RELATIVE_FILE)) {
      parts.sqliteFile = portableSqliteFile();
    } else {
      parts.sqliteFile = path;
    }
    return parts;
  }

  private static String composeSqlite(Parts parts, Path installHome) {
    String file = parts.sqliteFile == null ? "" : parts.sqliteFile.trim();
    if (file.isBlank()) {
      file = portableSqliteFile();
    }
    String path;
    if (isPortableSqliteFile(file) || file.contains("${IDSERVER_HOME}") || file.contains("${idserver.home}")) {
      path = portableSqliteFile();
    } else {
      Path p = Path.of(file);
      if (!p.isAbsolute() && installHome != null) {
        p = installHome.resolve(file);
      }
      path = p.toAbsolutePath().normalize().toString().replace('\\', '/');
      Path home = installHome == null ? null : installHome.toAbsolutePath().normalize();
      if (home != null) {
        String homeAbs = home.toString().replace('\\', '/');
        if (path.equalsIgnoreCase(homeAbs + "/" + SqlitePaths.RELATIVE_FILE)) {
          path = portableSqliteFile();
        }
      }
    }
    String extra = stripLeadingQuery(parts.extra);
    if (extra.isBlank()) {
      extra = SQLITE_DEFAULT_EXTRA;
    }
    return "jdbc:sqlite:" + path + "?" + extra;
  }

  private static Parts parseSqlServer(String url) {
    Matcher m = SQLSERVER.matcher(url);
    if (!m.matches()) {
      return rawOnly("sqlserver", url);
    }
    Parts parts = new Parts();
    parts.provider = "sqlserver";
    parts.host = m.group(1);
    parts.port = m.group(2) == null ? Integer.toString(defaultPort("sqlserver")) : m.group(2);
    Map<String, String> props = splitSemi(m.group(3));
    parts.database = first(props, "databaseName", "database");
    props.remove("databaseName");
    props.remove("database");
    removeIgnoreCase(props, "journal_mode");
    removeIgnoreCase(props, "busy_timeout");
    // Keys that still look like SQLite query fragments (value contains &).
    props.entrySet().removeIf(e -> e.getValue() != null && e.getValue().contains("&"));
    parts.extra = joinSemi(props);
    if (parts.extra.isBlank() || isStaleExtra("sqlserver", parts.extra)) {
      parts.extra = SQLSERVER_DEFAULT_EXTRA;
    }
    return parts;
  }

  private static String composeSqlServer(Parts parts) {
    String host = blankTo(parts.host, "localhost");
    String port = blankTo(parts.port, Integer.toString(defaultPort("sqlserver")));
    StringBuilder sb = new StringBuilder("jdbc:sqlserver://").append(host).append(':').append(port);
    LinkedHashMap<String, String> props = new LinkedHashMap<>();
    if (parts.database != null && !parts.database.isBlank()) {
      props.put("databaseName", parts.database.trim());
    }
    // Drop leftovers from SQLite (?a=b&c=d) — SQL Server only accepts ;key=value.
    props.putAll(splitSemi(sanitizeSqlServerExtra(parts.extra)));
    // mssql-jdbc 12 defaults encrypt=true; UAT/dev certs usually need trustServerCertificate.
    if (!containsIgnoreCase(props, "encrypt")) {
      props.put("encrypt", "true");
    }
    if (!containsIgnoreCase(props, "trustServerCertificate")) {
      props.put("trustServerCertificate", "true");
    }
    if (!containsIgnoreCase(props, "loginTimeout")) {
      props.put("loginTimeout", "15");
    }
    if (!props.isEmpty()) {
      sb.append(';').append(joinSemi(props));
    }
    return sb.toString();
  }

  /** True when Extra still looks like another provider's default (safe to replace on type change). */
  public static boolean isStaleExtra(String provider, String extra) {
    String prov = canonicalize(provider);
    String value = extra == null ? "" : extra.trim();
    if (value.isBlank()) {
      return true;
    }
    if (value.equalsIgnoreCase(defaultExtra(prov))) {
      return false;
    }
    for (String other : new String[] {"sqlite", "sqlserver", "postgresql", "mysql"}) {
      if (other.equals(prov)) {
        continue;
      }
      String def = defaultExtra(other);
      if (!def.isBlank() && value.equalsIgnoreCase(def)) {
        return true;
      }
    }
    if (!"sqlite".equals(prov)
        && (value.contains("journal_mode=") || value.contains("busy_timeout="))) {
      return true;
    }
    if ("sqlserver".equals(prov) && (value.contains("&") || value.contains("?"))) {
      return true;
    }
    return false;
  }

  private static String sanitizeSqlServerExtra(String extra) {
    if (extra == null || extra.isBlank()) {
      return "";
    }
    String value = extra.trim();
    if (value.contains("journal_mode=") || value.contains("busy_timeout=") || value.contains("&")) {
      return SQLSERVER_DEFAULT_EXTRA;
    }
    return value;
  }

  private static boolean containsIgnoreCase(Map<String, String> props, String key) {
    if (props == null || key == null) {
      return false;
    }
    for (String existing : props.keySet()) {
      if (existing != null && existing.equalsIgnoreCase(key)) {
        return true;
      }
    }
    return false;
  }

  private static void removeIgnoreCase(Map<String, String> props, String key) {
    if (props == null || key == null) {
      return;
    }
    props.keySet().removeIf(existing -> existing != null && existing.equalsIgnoreCase(key));
  }

  private static int indexOfIgnoreCase(String haystack, String needle) {
    if (haystack == null || needle == null) {
      return -1;
    }
    return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
  }

  private static Parts parsePostgres(String url) {
    Matcher m = POSTGRES.matcher(url);
    if (!m.matches()) {
      return rawOnly("postgresql", url);
    }
    Parts parts = new Parts();
    parts.provider = "postgresql";
    parts.host = m.group(1);
    parts.port = m.group(2) == null ? Integer.toString(defaultPort("postgresql")) : m.group(2);
    parts.database = m.group(3) == null ? "" : m.group(3);
    parts.extra = m.group(4) == null ? "" : m.group(4);
    return parts;
  }

  private static String composePostgres(Parts parts) {
    String host = blankTo(parts.host, "localhost");
    String port = blankTo(parts.port, Integer.toString(defaultPort("postgresql")));
    String db = parts.database == null ? "" : parts.database.trim();
    StringBuilder sb =
        new StringBuilder("jdbc:postgresql://").append(host).append(':').append(port).append('/').append(db);
    String extra = stripLeadingQuery(parts.extra);
    if (!extra.isBlank()) {
      sb.append('?').append(extra);
    }
    return sb.toString();
  }

  private static Parts parseMysql(String url) {
    Matcher m = MYSQL.matcher(url);
    if (!m.matches()) {
      return rawOnly("mysql", url);
    }
    Parts parts = new Parts();
    parts.provider = "mysql";
    parts.host = m.group(1);
    parts.port = m.group(2) == null ? Integer.toString(defaultPort("mysql")) : m.group(2);
    parts.database = m.group(3) == null ? "" : m.group(3);
    parts.extra = m.group(4) == null ? "" : m.group(4);
    return parts;
  }

  private static String composeMysql(Parts parts) {
    String host = blankTo(parts.host, "localhost");
    String port = blankTo(parts.port, Integer.toString(defaultPort("mysql")));
    String db = parts.database == null ? "" : parts.database.trim();
    StringBuilder sb =
        new StringBuilder("jdbc:mysql://").append(host).append(':').append(port).append('/').append(db);
    String extra = stripLeadingQuery(parts.extra);
    if (!extra.isBlank()) {
      sb.append('?').append(extra);
    }
    return sb.toString();
  }

  private static Parts rawOnly(String provider, String url) {
    Parts parts = new Parts();
    parts.provider = canonicalize(provider);
    parts.rawOnly = true;
    parts.rawUrl = url;
    parts.port = defaultPort(parts.provider) > 0 ? Integer.toString(defaultPort(parts.provider)) : "";
    parts.extra = defaultExtra(parts.provider);
    return parts;
  }

  private static Map<String, String> splitSemi(String raw) {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) {
      return map;
    }
    for (String piece : raw.split(";")) {
      String item = piece.trim();
      if (item.isEmpty()) {
        continue;
      }
      int eq = item.indexOf('=');
      if (eq <= 0) {
        map.put(item, "");
      } else {
        map.put(item.substring(0, eq).trim(), item.substring(eq + 1).trim());
      }
    }
    return map;
  }

  private static String joinSemi(Map<String, String> props) {
    if (props == null || props.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : props.entrySet()) {
      if (e.getKey() == null || e.getKey().isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append(e.getKey());
      if (e.getValue() != null && !e.getValue().isEmpty()) {
        sb.append('=').append(e.getValue());
      }
    }
    return sb.toString();
  }

  private static String first(Map<String, String> props, String... keys) {
    for (String key : keys) {
      if (props.containsKey(key)) {
        String value = props.get(key);
        return value == null ? "" : value;
      }
      for (Map.Entry<String, String> e : props.entrySet()) {
        if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
          return e.getValue() == null ? "" : e.getValue();
        }
      }
    }
    return "";
  }

  private static String stripLeadingQuery(String extra) {
    if (extra == null) {
      return "";
    }
    String value = extra.trim();
    while (value.startsWith("?")) {
      value = value.substring(1).trim();
    }
    return value;
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String canonicalize(String provider) {
    if (provider == null || provider.isBlank()) {
      return "sqlite";
    }
    return switch (provider.trim().toLowerCase(Locale.ROOT)) {
      case "postgresql", "postgres" -> "postgresql";
      case "mysql" -> "mysql";
      case "sqlserver", "mssql" -> "sqlserver";
      case "sqlite" -> "sqlite";
      default -> provider.trim().toLowerCase(Locale.ROOT);
    };
  }
}
