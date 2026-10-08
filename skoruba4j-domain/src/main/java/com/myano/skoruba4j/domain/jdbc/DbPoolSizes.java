package com.myano.skoruba4j.domain.jdbc;

import com.myano.skoruba4j.domain.DbProvider;

/** Hikari pool size from console / YAML. SQLite stays small because writers serialize. */
public final class DbPoolSizes {
  public static final int SQLITE_DEFAULT = 4;
  public static final int SERVER_DEFAULT = 8;
  public static final int SQLITE_MAX = 8;
  public static final int SERVER_MAX = 32;

  private DbPoolSizes() {}

  public static int resolve(DbProvider provider, int configured) {
    DbProvider p = provider == null ? DbProvider.SQLITE : provider;
    int fallback = p == DbProvider.SQLITE ? SQLITE_DEFAULT : SERVER_DEFAULT;
    int max = p == DbProvider.SQLITE ? SQLITE_MAX : SERVER_MAX;
    if (configured <= 0) {
      return fallback;
    }
    return Math.min(max, Math.max(1, configured));
  }
}
