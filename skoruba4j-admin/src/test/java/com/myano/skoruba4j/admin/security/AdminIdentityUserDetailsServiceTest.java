package com.myano.skoruba4j.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.TableStyle;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.security.core.userdetails.UserDetails;

class AdminIdentityUserDetailsServiceTest {

  @TempDir Path temp;

  @Test
  void loadsDemoUserByNameWithAdminRole() throws Exception {
    Path file = SqlitePaths.file(temp);
    Files.createDirectories(file.getParent());
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl(SqlitePaths.jdbcUrl(file));
    SqliteSchema.ensure(ds, TableStyle.SKORUBA);
    JdbcRepositories repos = new JdbcRepositories(ds, DbProvider.SQLITE, TableStyle.SKORUBA);
    AdminIdentityUserDetailsService service =
        new AdminIdentityUserDetailsService(Optional.of(repos));
    UserDetails details = service.loadUserByUsername("demo");
    AdminIdentityUser user = assertInstanceOf(AdminIdentityUser.class, details);
    assertEquals("demo", user.userName());
    assertEquals("demo@localhost", user.email());
    assertTrue(
        user.getAuthorities().stream().anyMatch(a -> SqliteSchema.DEMO_ROLE.equals(a.getAuthority())));
    assertEquals(user.id(), details.getUsername());
  }
}
