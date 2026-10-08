package com.myano.skoruba4j.console.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.console.configfile.LocalConfigFile;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.configstore.ClientWrite;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import com.myano.skoruba4j.domain.DbProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class JdbcClientChoicesTest {

  @TempDir Path temp;

  @Test
  void listsEnabledClientsAndPrefersAdminUiRedirect() throws Exception {
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
                "other-api",
                "Other API",
                true,
                true,
                false,
                false,
                3600,
                2_592_000,
                List.of("client_credentials"),
                List.of("api"),
                List.of(),
                List.of()));
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
    repos.roles().insert("Staff");
    repos.roles().insert("Admin");
    LocalConfigFile.Form form = new LocalConfigFile.Form();
    form.provider = "sqlite";
    form.url = SqlitePaths.jdbcUrl(file);
    form.tableStyle = "skoruba";
    JdbcClientChoices.Result result = JdbcClientChoices.load(form, temp);
    assertEquals("", result.error());
    assertEquals(2, result.items().size());
    assertEquals("copied-admin", result.items().get(0).clientId());
    assertTrue(result.items().get(0).adminUi());
    assertEquals(
        "copied-admin", JdbcClientChoices.preferred(result.items(), "skoruba4j-admin").clientId());
    assertEquals("other-api", JdbcClientChoices.preferred(result.items(), "other-api").clientId());
    assertEquals(List.of("Admin", "Staff"), result.roles());
    assertEquals("Staff", JdbcClientChoices.preferredRole(result.roles(), "Staff"));
    assertEquals("Admin", JdbcClientChoices.preferredRole(result.roles(), "MyRole"));
  }

  @Test
  void blankJdbcUrlDoesNotConnect() {
    JdbcClientChoices.Result result = JdbcClientChoices.load(new LocalConfigFile.Form(), Path.of("."));
    assertTrue(result.items().isEmpty());
    assertTrue(result.error().contains("JDBC URL"));
  }
}
