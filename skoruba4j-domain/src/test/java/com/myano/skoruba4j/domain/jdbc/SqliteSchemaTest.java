package com.myano.skoruba4j.domain.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.domain.password.PasswordVerificationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SqliteSchemaTest {

  @TempDir Path temp;

  @Test
  void bootstrapsDemoUserAndAdminClient() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl(SqlitePaths.jdbcUrl(file));
    SqliteSchema.ensure(ds, TableStyle.SKORUBA);
    SqliteSchema.ensure(ds, TableStyle.SKORUBA);
    JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
    assertEquals(1, repos.users().count());
    assertEquals(3, repos.clients().count());
    IdentityUser user = repos.users().search(new PageQuery("demo", 1, 10)).items().get(0);
    assertEquals("demo", user.userName());
    assertEquals(
        PasswordVerificationResult.SUCCESS,
        new IdentityPasswordHasher().verify(user.passwordHash(), SqliteSchema.DEMO_PASSWORD));
    Set<String> clientIds =
        repos.clients().search(new PageQuery("", 1, 10)).items().stream()
            .map(c -> c.clientId())
            .collect(Collectors.toSet());
    assertTrue(clientIds.contains(SqliteSchema.DEMO_CLIENT_ID));
    assertTrue(clientIds.contains(SqliteSchema.DEMO_GOOGLE_CLIENT_ID));
    assertTrue(clientIds.contains(SqliteSchema.DEMO_ADMIN_API_CLIENT_ID));
    ClientConfiguration google =
        repos.clients().findEnabledByClientId(SqliteSchema.DEMO_GOOGLE_CLIENT_ID).orElseThrow();
    ExternalLoginClientSettings ext = ExternalLoginClientSettings.from(google);
    assertTrue(ext.googleEnabled());
    assertEquals(ExternalLoginLinkMode.AUTO_CREATE, ext.googleLinkMode());
    assertTrue(ext.microsoftEnabled());
    assertEquals(ExternalLoginLinkMode.AUTO_CREATE, ext.microsoftLinkMode());
    ClientConfiguration adminApi =
        repos.clients().findEnabledByClientId(SqliteSchema.DEMO_ADMIN_API_CLIENT_ID).orElseThrow();
    ExternalLoginClientSettings apiExt = ExternalLoginClientSettings.from(adminApi);
    assertTrue(apiExt.googleEnabled());
    assertTrue(apiExt.microsoftEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, apiExt.googleLinkMode());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, apiExt.microsoftLinkMode());
    assertTrue(apiExt.whatsappEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, apiExt.whatsappLinkMode());
    assertTrue(apiExt.wechatEnabled());
    assertEquals(ExternalLoginLinkMode.LINK_EXISTING, apiExt.wechatLinkMode());
    assertTrue(
        adminApi.redirectUris().stream().anyMatch(u -> u.contains(":44302/signin-oidc")));
    try (var connection = DriverManager.getConnection(SqlitePaths.jdbcUrl(file));
        var st = connection.createStatement();
        var rs = st.executeQuery("PRAGMA journal_mode")) {
      assertEquals("wal", rs.getString(1).toLowerCase());
    }
    assertTrue(Files.exists(file));
  }

  @Test
  void copiedDatabaseKeepsExistingClientsAndDoesNotInventSkorubaAdmin() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl(SqlitePaths.jdbcUrl(file));
    SqliteSchema.ensure(ds, TableStyle.SKORUBA);
    JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
    int adminPk =
        repos.clients().findEnabledByClientId(SqliteSchema.DEMO_CLIENT_ID).orElseThrow().id();
    repos.clients().delete(adminPk);
    SqliteSchema.ensure(ds, TableStyle.SKORUBA);
    assertTrue(repos.clients().findEnabledByClientId(SqliteSchema.DEMO_CLIENT_ID).isEmpty());
    assertTrue(repos.clients().findEnabledByClientId(SqliteSchema.DEMO_GOOGLE_CLIENT_ID).isPresent());
    assertTrue(
        repos.clients().findEnabledByClientId(SqliteSchema.DEMO_ADMIN_API_CLIENT_ID).isPresent());
    assertEquals(1, repos.users().count());
  }
}
