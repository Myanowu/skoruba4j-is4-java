package com.myano.skoruba4j.domain.configstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientRepositorySqlitePoolTest {

  @TempDir Path temp;

  @Test
  void findEnabledByClientIdDoesNotDeadlockOnPoolOfOne() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(SqlitePaths.jdbcUrl(file));
    ds.setMaximumPoolSize(1);
    ds.setConnectionTimeout(3_000);
    ds.setPoolName("sqlite-pool-1");
    try {
      SqliteSchema.createEmpty(ds, TableStyle.SKORUBA);
      JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
      repos
          .clients()
          .insert(
              ClientWrite.basic(
                  "copied-admin",
                  "Copied Admin",
                  true,
                  true,
                  true,
                  false,
                  3600,
                  2_592_000,
                  List.of("authorization_code"),
                  List.of("openid"),
                  List.of("https://localhost:6061/signin-oidc"),
                  List.of("https://localhost:6061/signout-callback-oidc")));
      ClientConfiguration loaded =
          repos.clients().findEnabledByClientId("copied-admin").orElseThrow();
      assertEquals("copied-admin", loaded.clientId());
      assertEquals(List.of("authorization_code"), loaded.grantTypes());
      assertEquals(List.of("openid"), loaded.scopes());
      assertTrue(loaded.redirectUris().stream().anyMatch(u -> u.contains("/signin-oidc")));
    } finally {
      ds.close();
    }
  }

  @Test
  void updateKeepsAndReplacesCorsClaimsAndProperties(@TempDir Path temp) throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(SqlitePaths.jdbcUrl(file));
    ds.setMaximumPoolSize(1);
    ds.setConnectionTimeout(3_000);
    try {
      SqliteSchema.createEmpty(ds, TableStyle.SKORUBA);
      JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
      int id =
          repos
              .clients()
              .insert(
                  new ClientWrite(
                      "spa",
                      "SPA",
                      true,
                      false,
                      true,
                      false,
                      false,
                      true,
                      300,
                      3600,
                      300,
                      2_592_000,
                      1_296_000,
                      0,
                      "",
                      "",
                      List.of("authorization_code"),
                      List.of("openid"),
                      List.of("https://localhost:6061/signin-oidc"),
                      List.of(),
                      List.of("https://localhost:3000"),
                      List.of("role=staff"),
                      List.of("skoruba=1")));
      ClientConfiguration loaded = repos.clients().findByPk(id).orElseThrow();
      assertEquals(List.of("https://localhost:3000"), loaded.corsOrigins());
      assertEquals(1, loaded.claims().size());
      assertEquals("role", loaded.claims().get(0).type());
      assertEquals("staff", loaded.claims().get(0).value());
      repos
          .clients()
          .update(
              id,
              new ClientWrite(
                  "spa",
                  "SPA",
                  true,
                  false,
                  true,
                  false,
                  true,
                  true,
                  400,
                  3600,
                  300,
                  2_592_000,
                  1_296_000,
                  0,
                  "https://localhost:6061/front-logout",
                  "",
                  List.of("authorization_code"),
                  List.of("openid"),
                  List.of("https://localhost:6061/signin-oidc"),
                  List.of(),
                  List.of("https://localhost:3000", "https://127.0.0.1:3000"),
                  List.of(),
                  List.of()));
      ClientConfiguration updated = repos.clients().findByPk(id).orElseThrow();
      assertTrue(updated.requireConsent());
      assertEquals(400, updated.identityTokenLifetime());
      assertEquals(2, updated.corsOrigins().size());
      assertEquals("https://localhost:6061/front-logout", updated.frontChannelLogoutUri());
      // Claims/properties are not wiped by update (dedicated add/delete APIs).
      assertEquals(1, updated.claims().size());
      assertEquals("role", updated.claims().get(0).type());
      assertEquals(1, updated.properties().size());
      assertEquals("skoruba", updated.properties().get(0).key());
      repos.clients().addClaim(id, "dept", "ops");
      repos.clients().addProperty(id, "env", "uat");
      ClientConfiguration afterAdd = repos.clients().findByPk(id).orElseThrow();
      assertEquals(2, afterAdd.claims().size());
      assertEquals(2, afterAdd.properties().size());
      int claimId = afterAdd.claims().get(1).id();
      int propId = afterAdd.properties().get(1).id();
      repos.clients().deleteClaim(id, claimId);
      repos.clients().deleteProperty(id, propId);
      ClientConfiguration afterDel = repos.clients().findByPk(id).orElseThrow();
      assertEquals(1, afterDel.claims().size());
      assertEquals(1, afterDel.properties().size());
    } finally {
      ds.close();
    }
  }
}
