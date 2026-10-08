package com.myano.skoruba4j.admin.config;

import com.myano.skoruba4j.domain.DbProvider;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.SqlitePaths;
import com.myano.skoruba4j.domain.jdbc.SqliteSchema;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IdserverProperties.class)
public class JdbcConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnExpression("!'${idserver.db.url:}'.trim().isEmpty()")
  public DataSource dataSource(IdserverProperties props) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(SqlitePaths.resolveJdbcUrl(props.getDb().getUrl()));
    ds.setUsername(blankToNull(props.getDb().getUsername()));
    ds.setPassword(blankToNull(props.getDb().getPassword()));
    ds.setDriverClassName(props.dbProvider().driverClassName());
    ds.setPoolName("skoruba4j-admin");
    applyPool(ds, props);
    return ds;
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public JdbcRepositories jdbcRepositories(DataSource dataSource, IdserverProperties props) {
    if (props.dbProvider() == DbProvider.SQLITE) {
      SqliteSchema.ensure(dataSource, props.tableStyle());
    }
    return new JdbcRepositories(dataSource, props.dbProvider(), props.tableStyle());
  }

  private static void applyPool(HikariDataSource ds, IdserverProperties props) {
    ds.setMaximumPoolSize(props.dbPoolSize());
    ds.setMinimumIdle(0);
    ds.setConnectionTimeout(10_000);
    if (props.dbProvider() == DbProvider.SQLITE) {
      ds.setConnectionInitSql("PRAGMA busy_timeout=8000");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
