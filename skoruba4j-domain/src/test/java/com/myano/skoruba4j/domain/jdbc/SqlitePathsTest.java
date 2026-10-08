package com.myano.skoruba4j.domain.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePathsTest {

  @TempDir Path temp;

  @Test
  void portableUrlIsRelativeToInstallHomePlaceholder() {
    assertTrue(SqlitePaths.PORTABLE_JDBC_URL.contains("${IDSERVER_HOME}/data/skoruba4j.sqlite"));
    assertFalse(SqlitePaths.PORTABLE_JDBC_URL.contains("C:/"));
  }

  @Test
  void resolveExpandsPlaceholderAgainstInstallHome() {
    String url = SqlitePaths.resolveJdbcUrl(SqlitePaths.PORTABLE_JDBC_URL, temp);
    String expected = temp.toAbsolutePath().normalize().toString().replace('\\', '/');
    assertTrue(url.startsWith("jdbc:sqlite:" + expected + "/data/skoruba4j.sqlite"));
    assertTrue(url.contains("journal_mode=WAL"));
  }

  @Test
  void resolveJoinsRelativeSqlitePathToInstallHome() {
    String url = SqlitePaths.resolveJdbcUrl("jdbc:sqlite:data/skoruba4j.sqlite", temp);
    String expected = temp.toAbsolutePath().normalize().toString().replace('\\', '/');
    assertEquals(
        "jdbc:sqlite:" + expected + "/data/skoruba4j.sqlite?journal_mode=WAL&busy_timeout=8000",
        url);
  }

  @Test
  void resolveDbFileExpandsPlaceholder() {
    Path resolved =
        SqlitePaths.resolveDbFile("${IDSERVER_HOME}/data/custom.sqlite", temp);
    assertEquals(temp.resolve("data").resolve("custom.sqlite").toAbsolutePath().normalize(), resolved);
  }

  @Test
  void createEmptyFileCreatesParentAndFile() throws Exception {
    Path target = temp.resolve("nested").resolve("empty.sqlite");
    assertEquals(SqlitePaths.CreateEmptyResult.CREATED, SqlitePaths.createEmptyFile(target));
    assertTrue(Files.isRegularFile(target));
    assertTrue(Files.size(target) > 0);
  }

  @Test
  void createEmptyFileDoesNotOverwrite() throws Exception {
    Path target = temp.resolve("keep.sqlite");
    Files.writeString(target, "not-sqlite-but-present");
    long before = Files.size(target);
    assertEquals(SqlitePaths.CreateEmptyResult.ALREADY_EXISTS, SqlitePaths.createEmptyFile(target));
    assertEquals(before, Files.size(target));
    assertEquals("not-sqlite-but-present", Files.readString(target));
  }

  @Test
  void ensureSqliteSuffixAppendsWhenMissing() {
    Path with = SqlitePaths.ensureSqliteSuffix(temp.resolve("a.sqlite"));
    Path without = SqlitePaths.ensureSqliteSuffix(temp.resolve("b"));
    assertEquals("a.sqlite", with.getFileName().toString());
    assertEquals("b.sqlite", without.getFileName().toString());
  }
}
