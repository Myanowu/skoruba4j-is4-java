package com.myano.skoruba4j.domain.jdbc;

import java.sql.SQLException;

public final class UncheckedSqlException extends RuntimeException {
  public UncheckedSqlException(SQLException cause) {
    super(cause.getMessage(), cause);
  }
}
