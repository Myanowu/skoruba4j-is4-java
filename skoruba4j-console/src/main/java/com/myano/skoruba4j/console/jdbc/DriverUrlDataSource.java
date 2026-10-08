package com.myano.skoruba4j.console.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** One-off JDBC for the console; not a pool. */
final class DriverUrlDataSource implements DataSource {
  private final String url;
  private final String username;
  private final String password;

  DriverUrlDataSource(String url, String username, String password) {
    this.url = url;
    this.username = username;
    this.password = password;
  }

  @Override
  public Connection getConnection() throws SQLException {
    if (username == null || username.isBlank()) {
      return DriverManager.getConnection(url);
    }
    return DriverManager.getConnection(url, username, password == null ? "" : password);
  }

  @Override
  public Connection getConnection(String user, String pass) throws SQLException {
    return DriverManager.getConnection(url, user, pass);
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
