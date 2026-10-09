package com.myano.skoruba4j.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoleRepositorySearchTest {

  @TempDir Path temp;

  @Test
  void searchTextMatchesExactRoleNameForCreateUserPageSizeOne() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(SqlitePaths.jdbcUrl(file));
    ds.setMaximumPoolSize(1);
    ds.setConnectionTimeout(3_000);
    try {
      SqliteSchema.createEmpty(ds, TableStyle.SKORUBA);
      JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
      repos.roles().insert("4SUser");
      repos.roles().insert("Admin");
      PageResult<IdentityRole> found =
          repos.roles().search(PageQuery.of("4SUser", 1, 1));
      assertEquals(1, found.totalCount());
      assertEquals(1, found.items().size());
      assertEquals("4SUser", found.items().get(0).name());
    } finally {
      ds.close();
    }
  }
}
