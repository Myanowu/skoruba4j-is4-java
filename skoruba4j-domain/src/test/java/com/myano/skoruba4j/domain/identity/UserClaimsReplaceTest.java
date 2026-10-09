package com.myano.skoruba4j.domain.identity;

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

class UserClaimsReplaceTest {

  @TempDir Path temp;

  @Test
  void replaceClaimsByTypesLeavesOtherTypesAlone() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(SqlitePaths.jdbcUrl(file));
    ds.setMaximumPoolSize(1);
    ds.setConnectionTimeout(3_000);
    try {
      SqliteSchema.createEmpty(ds, TableStyle.SKORUBA);
      JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
      String id = repos.users().insert("orguser", "org@example.com", true, "hash");
      repos.users().addClaim(id, "locale", "zh-Hant");
      repos.users().addClaim(id, "department", "Old");
      repos
          .users()
          .replaceClaimsByTypes(
              id,
              List.of(
                  new UserClaim(0, "department", "Finance"),
                  new UserClaim(0, "org_path", "/HQ/Finance")));
      List<UserClaim> claims = repos.users().listClaims(id);
      assertEquals(3, claims.size());
      assertTrue(claims.stream().anyMatch(c -> "locale".equals(c.type()) && "zh-Hant".equals(c.value())));
      assertTrue(
          claims.stream().anyMatch(c -> "department".equals(c.type()) && "Finance".equals(c.value())));
      assertTrue(
          claims.stream()
              .anyMatch(c -> "org_path".equals(c.type()) && "/HQ/Finance".equals(c.value())));
      assertTrue(claims.stream().noneMatch(c -> "Old".equals(c.value())));
    } finally {
      ds.close();
    }
  }
}
