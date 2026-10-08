package com.myano.skoruba4j.console.configfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcConnectionStoreTest {

  @TempDir Path dir;

  @Test
  void roundTripYaml() throws Exception {
    JdbcConnectionStore.Store store = new JdbcConnectionStore.Store();
    JdbcConnectionStore.Connection a = new JdbcConnectionStore.Connection();
    a.id = "local-sqlite";
    a.name = "Local SQLite";
    a.provider = "sqlite";
    a.url = LocalConfigFile.EMBEDDED_JDBC_URL;
    a.tableStyle = "skoruba";
    JdbcConnectionStore.Connection b = new JdbcConnectionStore.Connection();
    b.id = "uat";
    b.name = "UAT SQL Server";
    b.provider = "sqlserver";
    b.url = "jdbc:sqlserver://127.0.0.1:1433;databaseName=demo";
    b.username = "sa";
    b.password = "secret";
    b.tableStyle = "skoruba";
    store.connections.add(a);
    store.connections.add(b);
    store.activeId = "uat";

    Path file = dir.resolve("config").resolve(JdbcConnectionStore.FILE_NAME);
    JdbcConnectionStore.save(file, store);
    assertTrue(Files.exists(file));

    JdbcConnectionStore.Store loaded =
        JdbcConnectionStore.parse(Files.readString(file));
    assertEquals(2, loaded.connections.size());
    assertEquals("uat", loaded.activeId);
    assertEquals("UAT SQL Server", loaded.find("uat").name);
    assertEquals("secret", loaded.find("uat").password);
    assertEquals(LocalConfigFile.EMBEDDED_JDBC_URL, loaded.find("local-sqlite").url);
  }

  @Test
  void uniqueNameAndApplyToForm() {
    JdbcConnectionStore.Store store = new JdbcConnectionStore.Store();
    JdbcConnectionStore.Connection a = new JdbcConnectionStore.Connection();
    a.id = "a";
    a.name = "Local";
    store.connections.add(a);
    assertEquals("Local (2)", JdbcConnectionStore.uniqueName(store, "Local"));
    assertTrue(JdbcConnectionStore.nameTaken(store, "local", null));
    assertFalse(JdbcConnectionStore.nameTaken(store, "local", "a"));

    LocalConfigFile.Form form = new LocalConfigFile.Form();
    JdbcConnectionStore.applyToForm(a, form);
    assertEquals("sqlite", form.provider);
    assertNotNull(form.tableStyle);
  }
}
