package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.configstore.ClientWrite;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * One-shot CREATE IF NOT EXISTS plus DEMO seed for embedded SQLite. Not Flyway; never run against
 * an existing IS4 SQL Server.
 */
public final class SqliteSchema {
  public static final String DEMO_USERNAME = "demo";
  public static final String DEMO_EMAIL = "demo@localhost";
  public static final String DEMO_PASSWORD = "Passw0rd!";
  public static final String DEMO_ROLE = "MyRole";
  public static final String DEMO_CLIENT_ID = "skoruba4j-admin";
  /** Demo RP with Google external login enabled (ClientProperties). */
  public static final String DEMO_GOOGLE_CLIENT_ID = "skoruba4j-google-demo";
  /** Admin API browser UI OIDC client (Google + Microsoft on STS). */
  public static final String DEMO_ADMIN_API_CLIENT_ID = "skoruba4j-admin-api";

  private static final String DDL_RESOURCE = "/com/myano/skoruba4j/domain/schema/sqlite-is4.sql";

  private SqliteSchema() {}

  /**
   * Plain-language first-login help after a fresh SQLite Initialize that seeded DEMO users.
   * Safe to show in Control UI (English) and docs.
   */
  public static String demoLoginGuide() {
    return "First login (fresh SQLite Initialize, Users was empty)\n"
        + "\n"
        + "  Username: "
        + DEMO_USERNAME
        + "\n"
        + "  Password: "
        + DEMO_PASSWORD
        + "\n"
        + "  Role:     "
        + DEMO_ROLE
        + "  (must match idserver.admin.role; default is MyRole)\n"
        + "\n"
        + "How to sign in\n"
        + "  • STS — open the STS login page; use the demo user above.\n"
        + "  • Admin — Control Settings → Admin sign-in (one mode):\n"
        + "      Local password, STS password (Admin form, no redirect), or STS OIDC (SSO).\n"
        + "      Demo user: "
        + DEMO_USERNAME
        + " / "
        + DEMO_PASSWORD
        + ". Role "
        + DEMO_ROLE
        + " required for data pages.\n"
        + "\n"
        + "STS password needs Client grant type password on "
        + DEMO_CLIENT_ID
        + "\n"
        + "(seeded on fresh Initialize; add manually on existing DBs).\n"
        + "\n"
        + "Google login DEMO client: "
        + DEMO_GOOGLE_CLIENT_ID
        + "\n"
        + "  ClientProperties: skoruba4j.external.google / microsoft = true, link-mode=auto-create.\n"
        + "  Needs global Google and/or Microsoft credentials on STS.\n"
        + "\n"
        + "Admin API UI client: "
        + DEMO_ADMIN_API_CLIENT_ID
        + "\n"
        + "  Redirects to Admin API :44302 /signin-oidc; Google + Microsoft link-existing"
        + " (no auto-create).\n"
        + "\n"
        + "Seed only runs when Users is empty. If the table already had rows,\n"
        + "Initialize does not create demo — use an existing user that has the admin role.";
  }

  /** One-line hint for banners / Overview. */
  public static String demoLoginHint() {
    return "After SQLite Initialize (empty Users): login "
        + DEMO_USERNAME
        + " / "
        + DEMO_PASSWORD
        + " (role "
        + DEMO_ROLE
        + ").";
  }

  public static void createEmpty(DataSource dataSource, TableStyle tableStyle) {
    IdentityTables identity = IdentityTables.forStyle(tableStyle);
    try (Connection connection = dataSource.getConnection()) {
      applyDdl(connection, identity);
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * CREATE IF NOT EXISTS, then seed DEMO user if Users is empty.
   *
   * @return {@code true} if DEMO seed was inserted
   */
  public static boolean ensure(DataSource dataSource, TableStyle tableStyle) {
    IdentityTables identity = IdentityTables.forStyle(tableStyle);
    try (Connection connection = dataSource.getConnection()) {
      applyDdl(connection, identity);
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
    try {
      boolean seeded = false;
      if (countUsers(dataSource, identity) == 0) {
        seed(dataSource, tableStyle);
        seeded = true;
      } else {
        // Existing DBs: still ensure DEMO external-login clients exist (idempotent).
        JdbcRepositories repos =
            new JdbcRepositories(dataSource, DbProvider.SQLITE, tableStyle);
        ensureDemoGoogleClient(repos);
        ensureDemoAdminApiClient(repos);
      }
      return seeded;
    } catch (UncheckedSqlException e) {
      String message = e.getMessage() == null ? "" : e.getMessage();
      if (message.contains("UNIQUE") || message.contains("unique")) {
        return false;
      }
      throw e;
    }
  }

  static void applyDdl(Connection connection, IdentityTables identity) throws SQLException {
    try (Statement pragma = connection.createStatement()) {
      pragma.execute("PRAGMA journal_mode=WAL");
      pragma.execute("PRAGMA busy_timeout=8000");
      pragma.execute("PRAGMA foreign_keys=ON");
    }
    for (String sql : identityDdl(identity)) {
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
      }
    }
    for (String sql : loadIs4Statements()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
      }
    }
  }

  static List<String> identityDdl(IdentityTables t) {
    String users = quote(t.users());
    String roles = quote(t.roles());
    String userRoles = quote(t.userRoles());
    String userClaims = quote(t.userClaims());
    String roleClaims = quote(t.roleClaims());
    String userLogins = quote(t.userLogins());
    String userTokens = quote(t.userTokens());
    return List.of(
        "CREATE TABLE IF NOT EXISTS "
            + users
            + " (Id TEXT NOT NULL PRIMARY KEY, UserName TEXT, NormalizedUserName TEXT, Email TEXT,"
            + " NormalizedEmail TEXT, EmailConfirmed INTEGER NOT NULL DEFAULT 0, PasswordHash TEXT,"
            + " SecurityStamp TEXT, ConcurrencyStamp TEXT, PhoneNumber TEXT,"
            + " PhoneNumberConfirmed INTEGER NOT NULL DEFAULT 0, TwoFactorEnabled INTEGER NOT NULL"
            + " DEFAULT 0, LockoutEnd TEXT, LockoutEnabled INTEGER NOT NULL DEFAULT 0,"
            + " AccessFailedCount INTEGER NOT NULL DEFAULT 0)",
        "CREATE UNIQUE INDEX IF NOT EXISTS UserNameIndex ON "
            + users
            + " (NormalizedUserName) WHERE NormalizedUserName IS NOT NULL",
        "CREATE TABLE IF NOT EXISTS "
            + roles
            + " (Id TEXT NOT NULL PRIMARY KEY, Name TEXT, NormalizedName TEXT, ConcurrencyStamp TEXT)",
        "CREATE TABLE IF NOT EXISTS "
            + userRoles
            + " (UserId TEXT NOT NULL, RoleId TEXT NOT NULL, PRIMARY KEY (UserId, RoleId))",
        "CREATE TABLE IF NOT EXISTS "
            + userClaims
            + " (Id INTEGER PRIMARY KEY AUTOINCREMENT, UserId TEXT NOT NULL, ClaimType TEXT, ClaimValue TEXT)",
        "CREATE TABLE IF NOT EXISTS "
            + roleClaims
            + " (Id INTEGER PRIMARY KEY AUTOINCREMENT, RoleId TEXT NOT NULL, ClaimType TEXT, ClaimValue TEXT)",
        "CREATE TABLE IF NOT EXISTS "
            + userLogins
            + " (LoginProvider TEXT NOT NULL, ProviderKey TEXT NOT NULL, ProviderDisplayName TEXT,"
            + " UserId TEXT NOT NULL, PRIMARY KEY (LoginProvider, ProviderKey))",
        "CREATE TABLE IF NOT EXISTS "
            + userTokens
            + " (UserId TEXT NOT NULL, LoginProvider TEXT NOT NULL, Name TEXT NOT NULL, Value TEXT,"
            + " PRIMARY KEY (UserId, LoginProvider, Name))");
  }

  static void seed(DataSource dataSource, TableStyle tableStyle) {
    JdbcRepositories repos = new JdbcRepositories(dataSource, DbProvider.SQLITE, tableStyle);
    String roleId = repos.roles().insert(DEMO_ROLE);
    String hash = new IdentityPasswordHasher().hash(DEMO_PASSWORD);
    String userId = repos.users().insert(DEMO_USERNAME, DEMO_EMAIL, true, hash);
    repos.users().addRole(userId, roleId);
    ensureIdentityResources(dataSource, repos);
    insertDemoClient(repos);
    ensureDemoGoogleClient(repos);
    ensureDemoAdminApiClient(repos);
  }

  static void ensureIdentityResources(DataSource dataSource, JdbcRepositories repos) {
    if (repos.identityResources().search(new PageQuery("openid", 1, 1)).totalCount() > 0) {
      return;
    }
    int openid = repos.identityResources().insert("openid", "Your user identifier", true);
    insertClaim(dataSource, openid, "sub");
    int profile = repos.identityResources().insert("profile", "User profile", true);
    insertClaim(dataSource, profile, "name");
    insertClaim(dataSource, profile, "preferred_username");
    int email = repos.identityResources().insert("email", "Email", true);
    insertClaim(dataSource, email, "email");
    int roles = repos.identityResources().insert("roles", "Roles", true);
    insertClaim(dataSource, roles, "role");
  }

  static void insertDemoClient(JdbcRepositories repos) {
    repos
        .clients()
        .insert(
            ClientWrite.basic(
                DEMO_CLIENT_ID,
                "skoruba4j-admin (DEMO)",
                true,
                false,
                true,
                false,
                3600,
                2_592_000,
                List.of("authorization_code", "password"),
                List.of("openid", "profile", "email", "roles"),
                List.of(
                    "https://localhost:6061/signin-oidc", "http://127.0.0.1:6060/signin-oidc"),
                List.of(
                    "https://localhost:6061/signout-callback-oidc",
                    "http://127.0.0.1:6060/signout-callback-oidc")));
  }

  /**
   * DEMO OAuth client with Google external login enabled. Idempotent — skips if {@link
   * #DEMO_GOOGLE_CLIENT_ID} already exists.
   */
  static void ensureDemoGoogleClient(JdbcRepositories repos) {
    var existing = repos.clients().findEnabledByClientId(DEMO_GOOGLE_CLIENT_ID);
    if (existing.isPresent()) {
      applyDemoExternalProperties(repos, existing.get().id());
      return;
    }
    // Also skip insert if disabled row exists with same ClientId; still try props if we can find pk
    if (repos.clients().search(new PageQuery(DEMO_GOOGLE_CLIENT_ID, 1, 5)).totalCount() > 0) {
      return;
    }
    int id =
        repos
            .clients()
            .insert(
                ClientWrite.basic(
                    DEMO_GOOGLE_CLIENT_ID,
                    "External login DEMO",
                    true,
                    false,
                    true,
                    false,
                    3600,
                    2_592_000,
                    List.of("authorization_code"),
                    List.of("openid", "profile", "email", "roles"),
                    List.of(
                        "https://localhost:6061/signin-oidc",
                        "http://127.0.0.1:6060/signin-oidc",
                        "https://127.0.0.1:6061/signin-oidc"),
                    List.of(
                        "https://localhost:6061/signout-callback-oidc",
                        "http://127.0.0.1:6060/signout-callback-oidc",
                        "https://127.0.0.1:6061/signout-callback-oidc")));
    applyDemoExternalProperties(repos, id);
  }

  /**
   * OIDC client for Admin API browser UI. Google + Microsoft on STS with {@code link-existing}
   * only (no auto-create). Idempotent.
   */
  static void ensureDemoAdminApiClient(JdbcRepositories repos) {
    var existing = repos.clients().findEnabledByClientId(DEMO_ADMIN_API_CLIENT_ID);
    if (existing.isPresent()) {
      applyAdminApiExternalProperties(repos, existing.get().id());
      return;
    }
    if (repos.clients().search(new PageQuery(DEMO_ADMIN_API_CLIENT_ID, 1, 5)).totalCount() > 0) {
      return;
    }
    int id =
        repos
            .clients()
            .insert(
                ClientWrite.basic(
                    DEMO_ADMIN_API_CLIENT_ID,
                    "skoruba4j-admin-api (DEMO)",
                    true,
                    false,
                    true,
                    false,
                    3600,
                    2_592_000,
                    List.of("authorization_code"),
                    List.of("openid", "profile", "email", "roles"),
                    List.of(
                        "https://localhost:44302/signin-oidc",
                        "https://127.0.0.1:44302/signin-oidc",
                        "http://localhost:44302/signin-oidc",
                        "http://127.0.0.1:44302/signin-oidc"),
                    List.of(
                        "https://localhost:44302/signout-callback-oidc",
                        "https://127.0.0.1:44302/signout-callback-oidc",
                        "http://localhost:44302/signout-callback-oidc",
                        "http://127.0.0.1:44302/signout-callback-oidc")));
    applyAdminApiExternalProperties(repos, id);
  }

  private static void applyDemoExternalProperties(JdbcRepositories repos, int id) {
    String auto = ExternalLoginLinkMode.AUTO_CREATE.configValue();
    repos.clients().setProperty(id, ExternalLoginClientSettings.GOOGLE_ENABLED_KEY, "true");
    repos.clients().setProperty(id, ExternalLoginClientSettings.GOOGLE_LINK_MODE_KEY, auto);
    repos.clients().setProperty(id, ExternalLoginClientSettings.MICROSOFT_ENABLED_KEY, "true");
    repos.clients().setProperty(id, ExternalLoginClientSettings.MICROSOFT_LINK_MODE_KEY, auto);
  }

  /** Admin API: external IdP only links to an existing Identity user (never auto-create). */
  private static void applyAdminApiExternalProperties(JdbcRepositories repos, int id) {
    String linkExisting = ExternalLoginLinkMode.LINK_EXISTING.configValue();
    repos.clients().setProperty(id, ExternalLoginClientSettings.GOOGLE_ENABLED_KEY, "true");
    repos.clients().setProperty(id, ExternalLoginClientSettings.GOOGLE_LINK_MODE_KEY, linkExisting);
    repos.clients().setProperty(id, ExternalLoginClientSettings.MICROSOFT_ENABLED_KEY, "true");
    repos
        .clients()
        .setProperty(id, ExternalLoginClientSettings.MICROSOFT_LINK_MODE_KEY, linkExisting);
    repos.clients().setProperty(id, ExternalLoginClientSettings.WHATSAPP_ENABLED_KEY, "true");
    repos
        .clients()
        .setProperty(id, ExternalLoginClientSettings.WHATSAPP_LINK_MODE_KEY, linkExisting);
    repos.clients().setProperty(id, ExternalLoginClientSettings.WECHAT_ENABLED_KEY, "true");
    repos
        .clients()
        .setProperty(id, ExternalLoginClientSettings.WECHAT_LINK_MODE_KEY, linkExisting);
  }

  private static void insertClaim(DataSource dataSource, int resourceId, String type) {
    Jdbc.execute(
        dataSource,
        "INSERT INTO IdentityResourceClaims (Type, IdentityResourceId) VALUES (?, ?)",
        type,
        resourceId);
  }

  private static int countUsers(DataSource dataSource, IdentityTables identity) {
    return Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + quote(identity.users()));
  }

  static List<String> loadIs4Statements() {
    try (InputStream in = SqliteSchema.class.getResourceAsStream(DDL_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + DDL_RESOURCE);
      }
      StringBuilder raw = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("--")) {
            continue;
          }
          raw.append(line).append('\n');
        }
      }
      List<String> statements = new ArrayList<>();
      for (String part : raw.toString().split(";")) {
        String sql = part.trim();
        if (!sql.isEmpty()) {
          statements.add(sql);
        }
      }
      return statements;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
