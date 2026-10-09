package com.myano.skoruba4j.domain.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class IdentityStoreAdminTest {

  @TempDir Path temp;

  @Test
  void initializeCreatesExpectedTablesAndDemoUser() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl(SqlitePaths.jdbcUrl(file));
    IdentityStoreAdmin.Report report =
        IdentityStoreAdmin.initializeSqlite(ds, TableStyle.SKORUBA);
    assertTrue(report.ping());
    assertEquals(IdentityStoreAdmin.expectedTables(TableStyle.SKORUBA).size(), report.presentCount());
    IdentityStoreAdmin.TableStatus users =
        report.tables().stream().filter(t -> "Users".equals(t.name())).findFirst().orElseThrow();
    assertTrue(users.present());
    assertEquals(1, users.rows());
    IdentityStoreAdmin.TableStatus clients =
        report.tables().stream().filter(t -> "Clients".equals(t.name())).findFirst().orElseThrow();
    assertEquals(1, clients.rows());
    IdentityStoreAdmin.Report again = IdentityStoreAdmin.inspect(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
    assertEquals(1, again.tables().stream().filter(t -> "Users".equals(t.name())).findFirst().orElseThrow().rows());

    IdentityStoreAdmin.TableSchema schema =
        IdentityStoreAdmin.describe(ds, DbProvider.SQLITE, TableStyle.SKORUBA, "Users");
    assertTrue(schema.error() == null || schema.error().isBlank());
    assertTrue(schema.columns().size() >= 5);
    assertTrue(
        schema.columns().stream()
            .anyMatch(c -> "UserName".equalsIgnoreCase(c.name()) || "Username".equalsIgnoreCase(c.name())));
    assertTrue(schema.columns().stream().anyMatch(IdentityStoreAdmin.ColumnDef::primaryKey));
    IdentityStoreAdmin.ColumnDef idCol =
        schema.columns().stream()
            .filter(c -> "Id".equalsIgnoreCase(c.name()))
            .findFirst()
            .orElseThrow();
    assertTrue(idCol.typeDisplay() != null && !idCol.typeDisplay().isBlank());

    IdentityStoreAdmin.Preview preview =
        IdentityStoreAdmin.preview(ds, DbProvider.SQLITE, TableStyle.SKORUBA, "Users", 100);
    assertTrue(preview.error() == null || preview.error().isBlank());
    assertTrue(preview.columns().contains("UserName") || preview.columns().contains("Username"));
    assertEquals(1, preview.rows().size());
    assertTrue(preview.sensitiveColumns().stream().anyMatch(Boolean::booleanValue));
    int hashIdx = -1;
    for (int i = 0; i < preview.columns().size(); i++) {
      if (preview.columns().get(i).toLowerCase().contains("password")) {
        hashIdx = i;
        break;
      }
    }
    assertTrue(hashIdx >= 0);
    assertEquals("••••••••", preview.rows().get(0).get(hashIdx));

    IdentityStoreAdmin.Preview revealed =
        IdentityStoreAdmin.preview(
            ds, DbProvider.SQLITE, TableStyle.SKORUBA, "Users", 0, 100, "Id", true);
    assertTrue(revealed.rows().get(0).get(hashIdx).length() > 8);
    assertTrue(!revealed.rows().get(0).get(hashIdx).startsWith("••••"));

    String userId = revealed.rows().get(0).get(revealed.columns().indexOf("Id"));
    IdentityStoreRowEditor.RowModel row =
        IdentityStoreRowEditor.load(
            ds, DbProvider.SQLITE, TableStyle.SKORUBA, "Users", java.util.Map.of("Id", userId));
    java.util.Map<String, String> proposed = new java.util.LinkedHashMap<>(row.values());
    proposed.put("Email", "ops-edit@example.com");
    int updated =
        IdentityStoreRowEditor.update(
            ds, DbProvider.SQLITE, TableStyle.SKORUBA, row, proposed, "NewPass!234");
    assertTrue(updated >= 1);
    IdentityStoreRowEditor.RowModel reloaded =
        IdentityStoreRowEditor.load(
            ds, DbProvider.SQLITE, TableStyle.SKORUBA, "Users", java.util.Map.of("Id", userId));
    assertEquals("ops-edit@example.com", reloaded.values().get("Email"));
    assertTrue(
        new com.myano.skoruba4j.domain.password.IdentityPasswordHasher()
                .verify(reloaded.values().get("PasswordHash"), "NewPass!234")
            != com.myano.skoruba4j.domain.password.PasswordVerificationResult.FAILED);
  }
}
