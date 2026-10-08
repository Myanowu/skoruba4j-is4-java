package com.myano.skoruba4j.domain.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class Jdbc {
  private static final ThreadLocal<Connection> BOUND = new ThreadLocal<>();

  @FunctionalInterface
  public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
  }

  @FunctionalInterface
  interface SqlWork<T> {
    T run(Connection connection) throws SQLException;
  }

  private Jdbc() {}

  public static <T> List<T> query(DataSource ds, String sql, RowMapper<T> mapper, Object... args) {
    return withConnection(
        ds,
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
              List<T> rows = new ArrayList<>();
              while (rs.next()) {
                rows.add(mapper.map(rs));
              }
              return rows;
            }
          }
        });
  }

  public static <T> java.util.Optional<T> queryOne(
      DataSource ds, String sql, RowMapper<T> mapper, Object... args) {
    List<T> rows = query(ds, sql, mapper, args);
    return rows.stream().findFirst();
  }

  public static int queryForInt(DataSource ds, String sql, Object... args) {
    return withConnection(
        ds,
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
              if (!rs.next()) {
                return 0;
              }
              return rs.getInt(1);
            }
          }
        });
  }

  public static int execute(DataSource ds, String sql, Object... args) {
    return withConnection(
        ds,
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
          }
        });
  }

  public static int insertReturningId(DataSource ds, String sql, Object... args) {
    return withConnection(
        ds,
        c -> {
          try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
              if (keys.next()) {
                return keys.getInt(1);
              }
            }
            return 0;
          }
        });
  }

  public static boolean ping(DataSource ds) {
    return withConnection(
        ds,
        c -> {
          try (PreparedStatement ps = c.prepareStatement("SELECT 1");
              ResultSet rs = ps.executeQuery()) {
            return rs.next();
          }
        });
  }

  static <T> T withConnection(DataSource ds, SqlWork<T> work) {
    Connection bound = BOUND.get();
    if (bound != null) {
      try {
        return work.run(bound);
      } catch (SQLException e) {
        throw new UncheckedSqlException(e);
      }
    }
    try (Connection c = ds.getConnection()) {
      BOUND.set(c);
      try {
        return work.run(c);
      } finally {
        BOUND.remove();
      }
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  private static void bind(PreparedStatement ps, Object... args) throws SQLException {
    if (args == null) {
      return;
    }
    for (int i = 0; i < args.length; i++) {
      Object v = args[i];
      if (v == null) {
        ps.setNull(i + 1, Types.VARCHAR);
      } else {
        ps.setObject(i + 1, v);
      }
    }
  }
}
