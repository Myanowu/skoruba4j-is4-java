package com.myano.skoruba4j.domain.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class IdentityStoreCopyTest {

  @TempDir Path temp;

  @Test
  void backupAndRestoreRoundTripSqlite() throws Exception {
    Path live = temp.resolve("live.sqlite");
    Path backup = temp.resolve("backup.sqlite");
    Path restored = temp.resolve("restored.sqlite");
    SQLiteDataSource source = new SQLiteDataSource();
    source.setUrl(SqlitePaths.jdbcUrl(live));
    IdentityStoreAdmin.initializeSqlite(source, TableStyle.SKORUBA);
    Map<String, Integer> saved = IdentityStoreCopy.backupToFile(source, backup, TableStyle.SKORUBA);
    assertTrue(saved.get("Users") >= 1);
    assertTrue(Files.exists(backup));

    SQLiteDataSource dest = new SQLiteDataSource();
    dest.setUrl(SqlitePaths.jdbcUrl(restored));
    IdentityStoreCopy.restoreFromFile(backup, dest, DbProvider.SQLITE, TableStyle.SKORUBA);
    IdentityStoreAdmin.Report report =
        IdentityStoreAdmin.inspect(dest, DbProvider.SQLITE, TableStyle.SKORUBA);
    assertEquals(
        1, report.tables().stream().filter(t -> "Users".equals(t.name())).findFirst().orElseThrow().rows());
    assertEquals(
        1,
        report.tables().stream()
            .filter(t -> "Clients".equals(t.name()))
            .findFirst()
            .orElseThrow()
            .rows());
  }
}
