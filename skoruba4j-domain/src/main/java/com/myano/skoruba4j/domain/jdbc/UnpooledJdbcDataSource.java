package com.myano.skoruba4j.domain.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** DriverManager-backed DataSource for one-off tools and tests. Not a pool. */
public final class UnpooledJdbcDataSource implements DataSource {
  private final String url;

  public UnpooledJdbcDataSource(String url) {
    this.url = Objects.requireNonNull(url, "url");
  }

  @Override
  public Connection getConnection() throws SQLException {
    return DriverManager.getConnection(url);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }

  @Override
  public PrintWriter getLogWriter() {
    return null;
  }

  @Override
  public void setLogWriter(PrintWriter out) {}

  @Override
  public void setLoginTimeout(int seconds) {}

  @Override
  public int getLoginTimeout() {
    return 0;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return Logger.getLogger("global");
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException("unwrap");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return false;
  }
}
