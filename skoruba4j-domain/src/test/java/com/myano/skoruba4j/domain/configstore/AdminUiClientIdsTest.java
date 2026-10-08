package com.myano.skoruba4j.domain.configstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class AdminUiClientIdsTest {

  @TempDir Path temp;

  @Test
  void usesConfiguredIdWhenItExistsOtherwiseSigninOidcClientFromCopy() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl(SqlitePaths.jdbcUrl(file));
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
    assertEquals(
        "copied-admin", AdminUiClientIds.resolve("skoruba4j-admin", Optional.of(repos)));
    assertEquals(
        "copied-admin", AdminUiClientIds.resolve("copied-admin", Optional.of(repos)));
    assertEquals(List.of("copied-admin"), repos.clients().listEnabledAdminUiClientIds());
    assertEquals(1, repos.clients().listEnabled(10).size());
  }
}
