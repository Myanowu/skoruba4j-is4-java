package com.myano.skoruba4j.console.configfile;

import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Shared gitignored YAML consumed by all three processes. */
public final class LocalConfigFile {
  public static final String DEFAULT_PROVIDER = "sqlite";
  public static final String[] PROVIDERS = {"sqlite", "sqlserver", "postgresql", "mysql"};
  public static final String EMBEDDED_JDBC_URL = SqlitePaths.PORTABLE_JDBC_URL;
  public static final String DEFAULT_TABLE_STYLE = "skoruba";
  public static final String DEFAULT_ISSUER_URI = "https://localhost:5051";
  public static final String DEFAULT_END_SESSION = "compatible";
  public static final String[] END_SESSION_MODES = {"compatible", "strict"};
  public static final String DEFAULT_CLIENT_ID = "skoruba4j-admin";
  public static final String DEFAULT_ADMIN_API_CLIENT_ID = "skoruba4j-admin-api";
  public static final String DEFAULT_ADMIN_ROLE = "MyRole";
  /** Control blank default: Admin form + STS password grant (MobileWeb-style, no browser SSO). */
  public static final String DEFAULT_LOGIN_MODE = "sts-password";
  public static final String[] LOGIN_MODES = {"local", "sts-password", "sts-oidc"};
  /** Admin API browser UI: local | sts-oidc | both. */
  public static final String DEFAULT_API_LOGIN_MODE = "both";
  public static final String[] API_LOGIN_MODES = {"local", "sts-oidc", "both"};
  public static final String DEFAULT_KEY_STORE = "file:${user.dir}/local-ssl.p12";
  public static final String DEFAULT_KEY_STORE_TYPE = "PKCS12";
  public static final String[] STORE_TYPES = {"PKCS12", "JKS"};
  public static final String DEFAULT_KEY_ALIAS = "localhost";
  public static final String[] PROTOCOLS = {"HTTPS", "HTTP"};
  public static final int DEFAULT_STS_PORT = 5051;
  public static final int DEFAULT_ADMIN_PORT = 6061;
  public static final int DEFAULT_ADMIN_API_PORT = 44302;
  public static final int DEFAULT_HEAP_MB = 512;
  public static final int DEFAULT_API_HEAP_MB = 256;
  public static final int DEFAULT_DB_POOL = 4;
  public static final String PROFILE_STS = "node-sts";
  public static final String PROFILE_ADMIN = "node-admin";
  public static final String PROFILE_ADMIN_API = "node-admin-api";

  private LocalConfigFile() {}

  public static final class Form {
    public String provider = "";
    public String url = "";
    public String username = "";
    public String password = "";
    public String tableStyle = "";
    public String issuerUri = "";
    public String endSession = "";
    /**
     * {@code local} | {@code sts-password} | {@code sts-oidc}. Default {@link
     * #DEFAULT_LOGIN_MODE}.
     */
    public String loginMode = DEFAULT_LOGIN_MODE;
    /**
     * Derived for Admin OIDC beans / older YAML: true only when {@link #loginMode} is {@code
     * sts-oidc}.
     */
    public boolean oidcEnabled = false;
    public String clientId = "";
    public String clientSecret = "";
    public String adminRole = "";
    /** Admin API browser UI ({@code /}, {@code /login}, {@code /ui/**}). Default on. */
    public boolean adminApiUiEnabled = true;
    /** {@code local} | {@code sts-oidc} | {@code both}. */
    public String adminApiLoginMode = DEFAULT_API_LOGIN_MODE;
    public String adminApiClientId = DEFAULT_ADMIN_API_CLIENT_ID;
    public String adminApiClientSecret = "";
    public boolean sslEnabled = true;
    public String keyStore = "";
    public String keyStorePassword = "";
    public String keyStoreType = "";
    public String keyAlias = "";
    public String trustStore = "";
    public String trustStorePassword = "";
    public String trustStoreType = "";
    public ProcessRuntime sts = ProcessRuntime.sts();
    public ProcessRuntime adminProc = ProcessRuntime.admin();
    public ProcessRuntime adminApi = ProcessRuntime.adminApi();
    public String smtpHost = "";
    public int smtpPort = 587;
    public String smtpUsername = "";
    public String smtpPassword = "";
    public String smtpFrom = "";
    public boolean smtpStartTls = true;
    public String smtpResetKey = "";
    /** STS external IdP (written under {@code idserver.external-login}). */
    public String googleClientId = "";
    public String googleClientSecret = "";
    public String microsoftClientId = "";
    public String microsoftClientSecret = "";
    public String microsoftTenantId = "";
    public String whatsappBusinessPhone = "";
    public String whatsappWebhookVerifyToken = "";
    public String whatsappAppSecret = "";
    public String wechatAppId = "";
    public String wechatAppSecret = "";
  }

  public static final class ProcessRuntime {
    public int port;
    public boolean sslEnabled = true;
    public int heapMb;
    public int dbPoolSize;

    public static ProcessRuntime sts() {
      return of(DEFAULT_STS_PORT, true, DEFAULT_HEAP_MB, DEFAULT_DB_POOL);
    }

    public static ProcessRuntime admin() {
      return of(DEFAULT_ADMIN_PORT, true, DEFAULT_HEAP_MB, DEFAULT_DB_POOL);
    }

    public static ProcessRuntime adminApi() {
      return of(DEFAULT_ADMIN_API_PORT, true, DEFAULT_API_HEAP_MB, DEFAULT_DB_POOL);
    }

    public static ProcessRuntime of(int port, boolean ssl, int heapMb, int pool) {
      ProcessRuntime runtime = new ProcessRuntime();
      runtime.port = clampPort(port, port);
      runtime.sslEnabled = ssl;
      runtime.heapMb = clampHeap(heapMb, heapMb);
      runtime.dbPoolSize = clampPool(pool);
      return runtime;
    }

    public String scheme() {
      return sslEnabled ? "https" : "http";
    }

    public String healthUrl(String host) {
      String h = host == null || host.isBlank() ? "localhost" : host.trim();
      return scheme() + "://" + h + ":" + port + "/health";
    }
  }

  public static Path resolve(Path repoRoot) {
    String env = System.getenv("IDSERVER_CONFIG");
    if (env != null && !env.isBlank()) {
      return Path.of(env.trim());
    }
    Path fromModule = Path.of("../config/idserver-local.yml");
    if (Files.exists(fromModule)) {
      return fromModule.toAbsolutePath().normalize();
    }
    if (repoRoot != null) {
      return repoRoot.resolve("config").resolve("idserver-local.yml");
    }
    return Path.of("config/idserver-local.yml").toAbsolutePath().normalize();
  }

  public static String read(Path file) throws IOException {
    if (file == null || !Files.exists(file)) {
      return "";
    }
    return Files.readString(file, StandardCharsets.UTF_8);
  }

  public static String yamlValue(String yaml, String key) {
    if (yaml == null || key == null) {
      return "";
    }
    String prefix = key + ":";
    for (String line : yaml.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith(prefix)) {
        String value = trimmed.substring(prefix.length()).trim();
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
          return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
      }
    }
    return "";
  }

  public static String firstNestedYamlValue(String parent, String key, String... yamls) {
    if (yamls == null) {
      return "";
    }
    for (String yaml : yamls) {
      String value = nestedYamlValue(yaml, parent, key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  /** First {@code key:} under a {@code parent:} mapping (does not steal db username/password). */
  public static String nestedYamlValue(String yaml, String parent, String key) {
    if (yaml == null || parent == null || key == null) {
      return "";
    }
    String parentPrefix = parent + ":";
    String keyPrefix = key + ":";
    boolean inParent = false;
    int parentIndent = -1;
    for (String line : yaml.split("\n", -1)) {
      if (line.trim().isEmpty() || line.trim().startsWith("#")) {
        continue;
      }
      if (line.startsWith("---")) {
        inParent = false;
        continue;
      }
      int indent = 0;
      while (indent < line.length() && line.charAt(indent) == ' ') {
        indent++;
      }
      String trimmed = line.trim();
      if (!inParent) {
        if (trimmed.equals(parentPrefix) || trimmed.startsWith(parentPrefix + " ")) {
          inParent = true;
          parentIndent = indent;
        }
        continue;
      }
      if (indent <= parentIndent) {
        inParent = false;
        if (trimmed.equals(parentPrefix) || trimmed.startsWith(parentPrefix + " ")) {
          inParent = true;
          parentIndent = indent;
        }
        continue;
      }
      if (trimmed.startsWith(keyPrefix)) {
        String value = trimmed.substring(keyPrefix.length()).trim();
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
          return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
      }
    }
    return "";
  }

  /**
   * Private Spring profile yaml ({@code application-local.yml}). Falls back to legacy {@code
   * application-elcss.yml} if present so older installs keep working.
   */
  public static Path localProfileFile(Path repoRoot) {
    if (repoRoot != null) {
      Path fromTomcat =
          repoRoot.resolve("tomcat").resolve("skoruba4j-sts").resolve("application-local.yml");
      if (Files.exists(fromTomcat)) {
        return fromTomcat;
      }
      Path legacyTomcat =
          repoRoot.resolve("tomcat").resolve("skoruba4j-sts").resolve("application-elcss.yml");
      if (Files.exists(legacyTomcat)) {
        return legacyTomcat;
      }
      Path fromModule = repoRoot.resolve("skoruba4j-sts").resolve("application-local.yml");
      if (Files.exists(fromModule)) {
        return fromModule;
      }
      Path legacyModule = repoRoot.resolve("skoruba4j-sts").resolve("application-elcss.yml");
      if (Files.exists(legacyModule)) {
        return legacyModule;
      }
    }
    return Path.of("skoruba4j-sts").resolve("application-local.yml");
  }

  /** @deprecated use {@link #localProfileFile(Path)} */
  @Deprecated
  public static Path elcssFile(Path repoRoot) {
    return localProfileFile(repoRoot);
  }

  public static String firstYamlValue(String key, String... yamls) {
    if (yamls == null) {
      return "";
    }
    for (String yaml : yamls) {
      String value = yamlValue(yaml, key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  /**
   * Loads Control form values. {@code privateYaml} is the optional Spring {@code local} profile
   * document (gitignored); overlay wins over it for shared keys.
   */
  public static Form load(String overlay, String privateYaml) {
    Form form = new Form();
    form.provider = canonicalizeProvider(firstYamlValue("provider", overlay, privateYaml));
    form.url = firstYamlValue("url", overlay, privateYaml);
    if ("sqlite".equals(form.provider) && (form.url == null || form.url.isBlank())) {
      form.url = EMBEDDED_JDBC_URL;
    }
    form.username = firstYamlValue("username", overlay, privateYaml);
    form.password = firstYamlValue("password", overlay, privateYaml);
    form.tableStyle =
        orDefault(firstYamlValue("table-style", overlay, privateYaml), DEFAULT_TABLE_STYLE);
    form.issuerUri =
        orDefault(firstYamlValue("issuer-uri", overlay, privateYaml), DEFAULT_ISSUER_URI);
    form.endSession =
        orDefault(firstYamlValue("end-session", overlay, privateYaml), DEFAULT_END_SESSION);
    form.loginMode = resolveLoginMode(overlay, privateYaml);
    form.oidcEnabled = "sts-oidc".equals(form.loginMode);
    form.clientId =
        preferExistingOverDemo(
            adminSetting(overlay, "client-id"),
            adminSetting(privateYaml, "client-id"),
            DEFAULT_CLIENT_ID);
    String adminSecret = adminSetting(overlay, "client-secret");
    if (adminSecret.isBlank()) {
      adminSecret = adminSetting(privateYaml, "client-secret");
    }
    form.clientSecret = adminSecret;
    form.adminRole =
        preferExistingOverDemo(
            adminSetting(overlay, "role"), adminSetting(privateYaml, "role"), DEFAULT_ADMIN_ROLE);
    String apiUiRaw = firstYamlValue("api-ui-enabled", overlay, privateYaml);
    form.adminApiUiEnabled = apiUiRaw.isBlank() || "true".equalsIgnoreCase(apiUiRaw);
    form.adminApiLoginMode =
        canonicalizeApiLoginMode(firstYamlValue("api-login-mode", overlay, privateYaml));
    form.adminApiClientId =
        orDefault(
            firstYamlValue("api-client-id", overlay, privateYaml), DEFAULT_ADMIN_API_CLIENT_ID);
    form.adminApiClientSecret = firstYamlValue("api-client-secret", overlay, privateYaml);
    String sslEnabled = firstYamlValue("enabled", overlay, privateYaml);
    form.sslEnabled = sslEnabled.isBlank() || "true".equalsIgnoreCase(sslEnabled);
    form.keyStore = orDefault(firstYamlValue("key-store", overlay, privateYaml), DEFAULT_KEY_STORE);
    form.keyStorePassword = firstYamlValue("key-store-password", overlay, privateYaml);
    form.keyStoreType =
        canonicalizeStoreType(firstYamlValue("key-store-type", overlay, privateYaml));
    form.keyAlias = orDefault(firstYamlValue("key-alias", overlay, privateYaml), DEFAULT_KEY_ALIAS);
    form.trustStore = firstYamlValue("trust-store", overlay, privateYaml);
    form.trustStorePassword = firstYamlValue("trust-store-password", overlay, privateYaml);
    form.trustStoreType =
        canonicalizeStoreType(firstYamlValue("trust-store-type", overlay, privateYaml));
    form.sts =
        loadRuntime(
            overlay, "sts", DEFAULT_STS_PORT, DEFAULT_HEAP_MB, form.sslEnabled);
    form.adminProc =
        loadRuntime(
            overlay, "admin", DEFAULT_ADMIN_PORT, DEFAULT_HEAP_MB, form.sslEnabled);
    form.adminApi =
        loadRuntime(
            overlay, "admin-api", DEFAULT_ADMIN_API_PORT, DEFAULT_API_HEAP_MB, form.sslEnabled);
    form.smtpHost = firstNestedYamlValue("smtp", "host", overlay, privateYaml);
    form.smtpPort = parseInt(firstNestedYamlValue("smtp", "port", overlay, privateYaml), 587);
    form.smtpUsername = firstNestedYamlValue("smtp", "username", overlay, privateYaml);
    form.smtpPassword = firstNestedYamlValue("smtp", "password", overlay, privateYaml);
    form.smtpFrom = firstNestedYamlValue("smtp", "from", overlay, privateYaml);
    String startTls = firstNestedYamlValue("smtp", "start-tls", overlay, privateYaml);
    form.smtpStartTls = startTls.isBlank() || "true".equalsIgnoreCase(startTls);
    form.smtpResetKey = firstNestedYamlValue("smtp", "reset-key", overlay, privateYaml);
    form.googleClientId = firstNestedYamlValue("google", "client-id", overlay, privateYaml);
    form.googleClientSecret = firstNestedYamlValue("google", "client-secret", overlay, privateYaml);
    form.microsoftClientId = firstNestedYamlValue("microsoft", "client-id", overlay, privateYaml);
    form.microsoftClientSecret =
        firstNestedYamlValue("microsoft", "client-secret", overlay, privateYaml);
    form.microsoftTenantId = firstNestedYamlValue("microsoft", "tenant-id", overlay, privateYaml);
    form.whatsappBusinessPhone =
        firstNestedYamlValue("whatsapp", "business-phone", overlay, privateYaml);
    form.whatsappWebhookVerifyToken =
        firstNestedYamlValue("whatsapp", "webhook-verify-token", overlay, privateYaml);
    form.whatsappAppSecret = firstNestedYamlValue("whatsapp", "app-secret", overlay, privateYaml);
    form.wechatAppId = firstNestedYamlValue("wechat", "app-id", overlay, privateYaml);
    form.wechatAppSecret = firstNestedYamlValue("wechat", "app-secret", overlay, privateYaml);
    return form;
  }

  static ProcessRuntime loadRuntime(
      String overlay, String prefix, int defaultPort, int defaultHeap, boolean defaultSsl) {
    int port = parseInt(yamlValue(overlay, prefix + "-port"), defaultPort);
    String sslRaw = yamlValue(overlay, prefix + "-ssl-enabled");
    boolean ssl = sslRaw.isBlank() ? defaultSsl : "true".equalsIgnoreCase(sslRaw);
    int heap = parseInt(yamlValue(overlay, prefix + "-heap-mb"), defaultHeap);
    int pool = parseInt(yamlValue(overlay, prefix + "-db-pool-size"), DEFAULT_DB_POOL);
    return ProcessRuntime.of(port, ssl, heap, pool);
  }

  public static String write(Form form, String previousYaml) {
    Form f = form == null ? new Form() : form;
    String keepPassword = f.password;
    if (keepPassword == null || keepPassword.isBlank()) {
      keepPassword = yamlValue(previousYaml, "password");
    }
    String keepKeyPassword = f.keyStorePassword;
    if (keepKeyPassword == null || keepKeyPassword.isBlank()) {
      keepKeyPassword = yamlValue(previousYaml, "key-store-password");
    }
    String keepTrustPassword = f.trustStorePassword;
    if (keepTrustPassword == null || keepTrustPassword.isBlank()) {
      keepTrustPassword = yamlValue(previousYaml, "trust-store-password");
    }
    String keepClientSecret = f.clientSecret;
    if (keepClientSecret == null || keepClientSecret.isBlank()) {
      keepClientSecret = adminSetting(previousYaml, "client-secret");
    }
    String keepApiClientSecret = f.adminApiClientSecret;
    if (keepApiClientSecret == null || keepApiClientSecret.isBlank()) {
      keepApiClientSecret = nestedYamlValue(previousYaml, "admin", "api-client-secret");
    }
    String keepSmtpPassword = f.smtpPassword;
    if (keepSmtpPassword == null || keepSmtpPassword.isBlank()) {
      keepSmtpPassword = nestedYamlValue(previousYaml, "smtp", "password");
    }
    String keepResetKey = f.smtpResetKey;
    if (keepResetKey == null || keepResetKey.isBlank()) {
      keepResetKey = nestedYamlValue(previousYaml, "smtp", "reset-key");
    }
    StringBuilder yaml = new StringBuilder();
    yaml.append("# Written by skoruba4j-console. Gitignored. Do not commit.\n");
    yaml.append("server:\n");
    yaml.append("  ssl:\n");
    yaml.append("    enabled: ").append(f.sslEnabled).append('\n');
    yaml.append("    key-store: ").append(quoted(orDefault(f.keyStore, DEFAULT_KEY_STORE))).append('\n');
    yaml.append("    key-store-password: ").append(quoted(keepKeyPassword)).append('\n');
    yaml.append("    key-store-type: ").append(canonicalizeStoreType(f.keyStoreType)).append('\n');
    yaml.append("    key-alias: ").append(quoted(orDefault(f.keyAlias, DEFAULT_KEY_ALIAS))).append('\n');
    yaml.append("idserver:\n");
    String provider = canonicalizeProvider(f.provider);
    String url = f.url;
    if ("sqlite".equals(provider) && (url == null || url.isBlank())) {
      url = EMBEDDED_JDBC_URL;
    }
    yaml.append("  db:\n");
    yaml.append("    provider: ").append(provider).append('\n');
    yaml.append("    url: ").append(quoted(url)).append('\n');
    yaml.append("    username: ").append(quoted(f.username)).append('\n');
    yaml.append("    password: ").append(quoted(keepPassword)).append('\n');
    yaml.append("  identity:\n");
    yaml.append("    table-style: ").append(scalar(f.tableStyle, DEFAULT_TABLE_STYLE)).append('\n');
    yaml.append("  issuer-uri: ").append(quoted(orDefault(f.issuerUri, DEFAULT_ISSUER_URI))).append('\n');
    yaml.append("  logout:\n");
    yaml.append("    end-session: ").append(canonicalizeEndSession(f.endSession)).append('\n');
    appendExternalLogin(yaml, f, previousYaml);
    yaml.append("  smtp:\n");
    yaml.append("    host: ").append(quoted(f.smtpHost)).append('\n');
    yaml.append("    port: ").append(clampPort(f.smtpPort, 587)).append('\n');
    yaml.append("    username: ").append(quoted(f.smtpUsername)).append('\n');
    yaml.append("    password: ").append(quoted(keepSmtpPassword)).append('\n');
    yaml.append("    from: ").append(quoted(f.smtpFrom)).append('\n');
    yaml.append("    start-tls: ").append(f.smtpStartTls).append('\n');
    yaml.append("    reset-key: ").append(quoted(keepResetKey)).append('\n');
    String loginMode = canonicalizeLoginMode(f.loginMode);
    boolean oidc = "sts-oidc".equals(loginMode);
    yaml.append("  admin:\n");
    yaml.append("    login-mode: ").append(loginMode).append('\n');
    yaml.append("    oidc-enabled: ").append(oidc).append('\n');
    yaml.append("    client-id: ").append(quoted(orDefault(f.clientId, DEFAULT_CLIENT_ID))).append('\n');
    yaml.append("    client-secret: ").append(quoted(keepClientSecret)).append('\n');
    yaml.append("    role: ").append(quoted(orDefault(f.adminRole, DEFAULT_ADMIN_ROLE))).append('\n');
    yaml.append("    api-ui-enabled: ").append(f.adminApiUiEnabled).append('\n');
    yaml.append("    api-login-mode: ")
        .append(canonicalizeApiLoginMode(f.adminApiLoginMode))
        .append('\n');
    yaml.append("    api-client-id: ")
        .append(quoted(orDefault(f.adminApiClientId, DEFAULT_ADMIN_API_CLIENT_ID)))
        .append('\n');
    yaml.append("    api-client-secret: ").append(quoted(keepApiClientSecret)).append('\n');
    yaml.append("  tls:\n");
    yaml.append("    trust-store: ").append(quoted(f.trustStore)).append('\n');
    yaml.append("    trust-store-password: ").append(quoted(keepTrustPassword)).append('\n');
    yaml.append("    trust-store-type: ").append(canonicalizeStoreType(f.trustStoreType)).append('\n');
    yaml.append("  console:\n");
    appendRuntime(yaml, "sts", f.sts, DEFAULT_STS_PORT, DEFAULT_HEAP_MB);
    appendRuntime(yaml, "admin", f.adminProc, DEFAULT_ADMIN_PORT, DEFAULT_HEAP_MB);
    appendRuntime(yaml, "admin-api", f.adminApi, DEFAULT_ADMIN_API_PORT, DEFAULT_API_HEAP_MB);
    appendProfileDocument(yaml, PROFILE_STS, f.sts);
    appendProfileDocument(yaml, PROFILE_ADMIN, f.adminProc);
    appendProfileDocument(yaml, PROFILE_ADMIN_API, f.adminApi);
    return yaml.toString();
  }

  /** Prefer {@code login-mode}; else map legacy {@code oidc-enabled}; else Control default. */
  static String resolveLoginMode(String overlay, String privateYaml) {
    String loginRaw = firstYamlValue("login-mode", overlay, privateYaml);
    if (!loginRaw.isBlank()) {
      return canonicalizeLoginMode(loginRaw);
    }
    String oidcRaw = firstYamlValue("oidc-enabled", overlay, privateYaml);
    if (!oidcRaw.isBlank()) {
      return "true".equalsIgnoreCase(oidcRaw) ? "sts-oidc" : "local";
    }
    return DEFAULT_LOGIN_MODE;
  }

  public static String canonicalizeLoginMode(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_LOGIN_MODE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    return switch (key) {
      case "local", "password", "form" -> "local";
      case "sts-password", "stspassword", "ropc", "password-grant" -> "sts-password";
      case "sts-oidc", "oidc", "sso", "sts" -> "sts-oidc";
      default -> DEFAULT_LOGIN_MODE;
    };
  }

  public static String canonicalizeApiLoginMode(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_API_LOGIN_MODE;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    return switch (key) {
      case "local", "password", "form" -> "local";
      case "sts-oidc", "oidc", "sso", "sts" -> "sts-oidc";
      case "both", "all", "hybrid" -> "both";
      default -> DEFAULT_API_LOGIN_MODE;
    };
  }

  static void appendRuntime(
      StringBuilder yaml, String prefix, ProcessRuntime runtime, int defaultPort, int defaultHeap) {
    ProcessRuntime r = runtime == null ? ProcessRuntime.of(defaultPort, true, defaultHeap, DEFAULT_DB_POOL) : runtime;
    yaml.append("    ").append(prefix).append("-port: ").append(clampPort(r.port, defaultPort)).append('\n');
    yaml.append("    ").append(prefix).append("-ssl-enabled: ").append(r.sslEnabled).append('\n');
    yaml.append("    ").append(prefix).append("-heap-mb: ").append(clampHeap(r.heapMb, defaultHeap)).append('\n');
    yaml.append("    ").append(prefix).append("-db-pool-size: ").append(clampPool(r.dbPoolSize)).append('\n');
  }

  static void appendProfileDocument(StringBuilder yaml, String profile, ProcessRuntime runtime) {
    ProcessRuntime r = runtime == null ? new ProcessRuntime() : runtime;
    yaml.append("---\n");
    yaml.append("spring:\n");
    yaml.append("  config:\n");
    yaml.append("    activate:\n");
    yaml.append("      on-profile: ").append(profile).append('\n');
    yaml.append("server:\n");
    yaml.append("  port: ").append(clampPort(r.port, r.port <= 0 ? 8080 : r.port)).append('\n');
    yaml.append("  ssl:\n");
    yaml.append("    enabled: ").append(r.sslEnabled).append('\n');
    yaml.append("idserver:\n");
    yaml.append("  db:\n");
    yaml.append("    pool-size: ").append(clampPool(r.dbPoolSize)).append('\n');
  }

  public static String jvmEnvCmd(Form form) {
    Form f = form == null ? new Form() : form;
    ProcessRuntime sts = f.sts == null ? ProcessRuntime.sts() : f.sts;
    ProcessRuntime admin = f.adminProc == null ? ProcessRuntime.admin() : f.adminProc;
    ProcessRuntime api = f.adminApi == null ? ProcessRuntime.adminApi() : f.adminApi;
    StringBuilder cmd = new StringBuilder();
    cmd.append("@echo off\n");
    cmd.append("REM Written by skoruba4j-console. Heap for start-*.cmd\n");
    cmd.append("set \"IDSERVER_STS_XMX=").append(clampHeap(sts.heapMb, DEFAULT_HEAP_MB)).append("m\"\n");
    cmd.append("set \"IDSERVER_ADMIN_XMX=").append(clampHeap(admin.heapMb, DEFAULT_HEAP_MB)).append("m\"\n");
    cmd.append("set \"IDSERVER_API_XMX=").append(clampHeap(api.heapMb, DEFAULT_API_HEAP_MB)).append("m\"\n");
    return cmd.toString();
  }

  public static ProcessRuntime runtimeFor(Form form, String module) {
    Form f = form == null ? new Form() : form;
    if ("skoruba4j-admin".equals(module)) {
      return f.adminProc;
    }
    if ("skoruba4j-admin-api".equals(module)) {
      return f.adminApi;
    }
    return f.sts;
  }

  public static String nodeProfile(String module) {
    if ("skoruba4j-admin".equals(module)) {
      return PROFILE_ADMIN;
    }
    if ("skoruba4j-admin-api".equals(module)) {
      return PROFILE_ADMIN_API;
    }
    return PROFILE_STS;
  }

  public static void save(Path file, String yaml) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, yaml, StandardCharsets.UTF_8);
  }

  public static String canonicalizeProvider(String value) {
    String raw = orDefault(value, DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
    return switch (raw) {
      case "postgresql", "postgres" -> "postgresql";
      case "mysql" -> "mysql";
      case "sqlite" -> "sqlite";
      case "sqlserver", "mssql" -> "sqlserver";
      default -> DEFAULT_PROVIDER;
    };
  }

  public static String embeddedJdbcUrl(Path installHome) {
    return EMBEDDED_JDBC_URL;
  }

  public static String canonicalizeEndSession(String value) {
    if (value != null && "strict".equalsIgnoreCase(value.trim())) {
      return "strict";
    }
    return DEFAULT_END_SESSION;
  }

  public static String canonicalizeStoreType(String value) {
    if (value != null && "jks".equalsIgnoreCase(value.trim())) {
      return "JKS";
    }
    return DEFAULT_KEY_STORE_TYPE;
  }

  public static String orDefault(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  /**
   * DEMO overlay keys must not hide the original IS4 Admin client/role from a copied database
   * overlay ({@code application-local.yml}).
   */
  /**
   * Admin client-id / secret / role: prefer {@code admin:} nesting; else legacy flat keys, skipping
   * {@code external-login} children (google/microsoft also use {@code client-id}).
   */
  static String adminSetting(String yaml, String key) {
    String nested = nestedYamlValue(yaml, "admin", key);
    if (!nested.isBlank()) {
      return nested;
    }
    return yamlValueOutsideBlock(yaml, key, "external-login");
  }

  /** Flat {@code key:} lookup that ignores lines inside an {@code idserver} child block. */
  static String yamlValueOutsideBlock(String yaml, String key, String skipBlock) {
    if (yaml == null || key == null) {
      return "";
    }
    String prefix = key + ":";
    String skip = skipBlock == null ? "" : skipBlock.trim() + ":";
    boolean skipping = false;
    int skipIndent = -1;
    for (String line : yaml.split("\n", -1)) {
      if (line.trim().isEmpty() || line.trim().startsWith("#")) {
        continue;
      }
      int indent = 0;
      while (indent < line.length() && line.charAt(indent) == ' ') {
        indent++;
      }
      String trimmed = line.trim();
      if (!skip.isBlank()) {
        if (!skipping
            && (trimmed.equals(skip) || trimmed.startsWith(skip + " ") || trimmed.startsWith(skip + "\t"))) {
          skipping = true;
          skipIndent = indent;
          continue;
        }
        if (skipping) {
          if (indent <= skipIndent && !trimmed.startsWith("#")) {
            skipping = false;
          } else {
            continue;
          }
        }
      }
      if (trimmed.startsWith(prefix)) {
        String value = trimmed.substring(prefix.length()).trim();
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
          return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
      }
    }
    return "";
  }

  static String preferExistingOverDemo(String overlay, String existing, String demoDefault) {
    String over = overlay == null ? "" : overlay.trim();
    String had = existing == null ? "" : existing.trim();
    if (!had.isBlank() && (over.isBlank() || demoDefault.equalsIgnoreCase(over))) {
      return had;
    }
    return orDefault(over, orDefault(had, demoDefault));
  }

  /** Spring {@code file:} location → filesystem path; {@code ${user.dir}} → module dir. */
  public static String filesystemPath(String springLocation, Path moduleDir) {
    if (springLocation == null || springLocation.isBlank()) {
      return "";
    }
    String raw = springLocation.trim();
    if (raw.startsWith("file:")) {
      raw = raw.substring("file:".length());
      if (raw.startsWith("///")) {
        raw = raw.substring(3);
      } else if (raw.startsWith("//")) {
        raw = raw.substring(2);
      }
    }
    if (moduleDir != null) {
      raw = raw.replace("${user.dir}", moduleDir.toAbsolutePath().toString());
    }
    return raw;
  }

  public static String springFileLocation(Path file) {
    if (file == null) {
      return "";
    }
    return "file:" + file.toAbsolutePath().toString().replace('\\', '/');
  }

  static String scalar(String value, String fallback) {
    return orDefault(value, fallback);
  }

  public static String quoted(String value) {
    String raw = value == null ? "" : value;
    return "'" + raw.replace("'", "''") + "'";
  }

  public static int parseInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  public static int clampPort(int port, int fallback) {
    int use = port <= 0 ? fallback : port;
    if (use < 1 || use > 65535) {
      return fallback;
    }
    return use;
  }

  public static int clampHeap(int heapMb, int fallback) {
    int use = heapMb <= 0 ? fallback : heapMb;
    if (use < 128) {
      return 128;
    }
    return Math.min(8192, use);
  }

  public static int clampPool(int pool) {
    if (pool <= 0) {
      return DEFAULT_DB_POOL;
    }
    return Math.min(32, Math.max(1, pool));
  }

  static void appendExternalLogin(StringBuilder yaml, Form f, String previousYaml) {
    String googleSecret = f.googleClientSecret;
    if (googleSecret == null || googleSecret.isBlank()) {
      googleSecret = nestedYamlValue(previousYaml, "google", "client-secret");
    }
    String msSecret = f.microsoftClientSecret;
    if (msSecret == null || msSecret.isBlank()) {
      msSecret = nestedYamlValue(previousYaml, "microsoft", "client-secret");
    }
    String waSecret = f.whatsappAppSecret;
    if (waSecret == null || waSecret.isBlank()) {
      waSecret = nestedYamlValue(previousYaml, "whatsapp", "app-secret");
    }
    String waToken = f.whatsappWebhookVerifyToken;
    if (waToken == null || waToken.isBlank()) {
      waToken = nestedYamlValue(previousYaml, "whatsapp", "webhook-verify-token");
    }
    String wxSecret = f.wechatAppSecret;
    if (wxSecret == null || wxSecret.isBlank()) {
      wxSecret = nestedYamlValue(previousYaml, "wechat", "app-secret");
    }
    yaml.append("  external-login:\n");
    yaml.append("    google:\n");
    yaml.append("      client-id: ").append(quoted(nullToEmpty(f.googleClientId))).append('\n');
    yaml.append("      client-secret: ").append(quoted(nullToEmpty(googleSecret))).append('\n');
    yaml.append("      allow-direct-login: false\n");
    yaml.append("    microsoft:\n");
    yaml.append("      client-id: ").append(quoted(nullToEmpty(f.microsoftClientId))).append('\n');
    yaml.append("      client-secret: ").append(quoted(nullToEmpty(msSecret))).append('\n');
    yaml.append("      tenant-id: ").append(quoted(nullToEmpty(f.microsoftTenantId))).append('\n');
    yaml.append("      allow-direct-login: false\n");
    yaml.append("    whatsapp:\n");
    yaml.append("      business-phone: ")
        .append(quoted(nullToEmpty(f.whatsappBusinessPhone)))
        .append('\n');
    yaml.append("      webhook-verify-token: ").append(quoted(nullToEmpty(waToken))).append('\n');
    yaml.append("      app-secret: ").append(quoted(nullToEmpty(waSecret))).append('\n');
    yaml.append("      allow-direct-login: false\n");
    yaml.append("    wechat:\n");
    yaml.append("      app-id: ").append(quoted(nullToEmpty(f.wechatAppId))).append('\n');
    yaml.append("      app-secret: ").append(quoted(nullToEmpty(wxSecret))).append('\n');
    yaml.append("      allow-direct-login: false\n");
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /**
   * Copies a direct child block under {@code idserver:} (e.g. {@code external-login}) from a prior
   * overlay so Control rewrite does not drop manually maintained secrets.
   */
  static String extractIdserverChildBlock(String yaml, String childKey) {
    if (yaml == null || yaml.isBlank() || childKey == null || childKey.isBlank()) {
      return "";
    }
    String marker = "  " + childKey.trim() + ":";
    int start = -1;
    String[] lines = yaml.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.equals(marker) || line.startsWith(marker + " ") || line.startsWith(marker + "\t")) {
        start = i;
        break;
      }
    }
    if (start < 0) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    out.append(lines[start]).append('\n');
    for (int i = start + 1; i < lines.length; i++) {
      String line = lines[i];
      if (line.startsWith("---")) {
        break;
      }
      if (!line.isEmpty() && !line.startsWith(" ") && !line.startsWith("\t") && !line.startsWith("#")) {
        break;
      }
      if (line.startsWith("  ")
          && !line.startsWith("   ")
          && !line.startsWith("  \t")
          && line.contains(":")
          && !line.startsWith("  #")) {
        // next idserver child at indent 2
        String trimmed = line.substring(2);
        if (!trimmed.startsWith(" ") && !trimmed.startsWith("\t") && trimmed.contains(":")) {
          break;
        }
      }
      out.append(line).append('\n');
    }
    return out.toString();
  }
}
