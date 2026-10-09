package com.myano.skoruba4j.domain.identity;

import com.myano.skoruba4j.domain.IdentityTables;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.jdbc.Jdbc;
import com.myano.skoruba4j.domain.jdbc.SqlDialect;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class UserRepository {
  private final DataSource dataSource;
  private final SqlDialect dialect;
  private final IdentityTables tables;

  public UserRepository(DataSource dataSource, SqlDialect dialect, IdentityTables tables) {
    this.dataSource = dataSource;
    this.dialect = dialect;
    this.tables = tables;
  }

  public int count() {
    String sql = "SELECT COUNT(*) FROM " + dialect.quote(tables.users());
    return Jdbc.queryForInt(dataSource, sql);
  }

  public List<IdentityUser> list(int limit) {
    String from =
        "FROM "
            + dialect.quote(tables.users())
            + " ORDER BY "
            + dialect.quote("NormalizedUserName");
    String sql =
        dialect.selectLimited(
            columns(), from, limit);
    return Jdbc.query(dataSource, sql, UserRepository::mapRow);
  }

  public Optional<IdentityUser> findByNormalizedUserName(String normalizedUserName) {
    String sql =
        "SELECT "
            + columns()
            + " FROM "
            + dialect.quote(tables.users())
            + " WHERE "
            + dialect.quote("NormalizedUserName")
            + " = ?";
    List<IdentityUser> rows =
        Jdbc.query(dataSource, sql, UserRepository::mapRow, normalizedUserName);
    return rows.stream().findFirst();
  }

  public Optional<IdentityUser> findById(String id) {
    String sql =
        "SELECT "
            + columns()
            + " FROM "
            + dialect.quote(tables.users())
            + " WHERE "
            + dialect.quote("Id")
            + " = ?";
    return Jdbc.query(dataSource, sql, UserRepository::mapRow, id).stream().findFirst();
  }

  public Optional<IdentityUser> findByNormalizedEmail(String normalizedEmail) {
    String sql =
        "SELECT "
            + columns()
            + " FROM "
            + dialect.quote(tables.users())
            + " WHERE "
            + dialect.quote("NormalizedEmail")
            + " = ?";
    List<IdentityUser> rows = Jdbc.query(dataSource, sql, UserRepository::mapRow, normalizedEmail);
    return rows.stream().findFirst();
  }

  private String columns() {
    return String.join(
        ", ",
        dialect.quote("Id"),
        dialect.quote("UserName"),
        dialect.quote("NormalizedUserName"),
        dialect.quote("Email"),
        dialect.quote("NormalizedEmail"),
        dialect.quote("EmailConfirmed"),
        dialect.quote("PasswordHash"),
        dialect.quote("SecurityStamp"),
        dialect.quote("LockoutEnabled"),
        dialect.quote("LockoutEnd"),
        dialect.quote("AccessFailedCount"),
        dialect.quote("TwoFactorEnabled"),
        dialect.quote("PhoneNumber"),
        dialect.quote("PhoneNumberConfirmed"));
  }

  public List<String> listRoleNames(String userId) {
    String sql =
        "SELECT r."
            + dialect.quote("Name")
            + " FROM "
            + dialect.quote(tables.userRoles())
            + " ur INNER JOIN "
            + dialect.quote(tables.roles())
            + " r ON r."
            + dialect.quote("Id")
            + " = ur."
            + dialect.quote("RoleId")
            + " WHERE ur."
            + dialect.quote("UserId")
            + " = ?";
    return Jdbc.query(dataSource, sql, rs -> rs.getString(1), userId);
  }

  public PageResult<IdentityUser> search(PageQuery query) {
    String table = dialect.quote(tables.users());
    String where = "";
    Object[] args = new Object[0];
    if (query.hasSearch()) {
      where =
          " WHERE ("
              + dialect.quote("NormalizedUserName")
              + " LIKE ? OR "
              + dialect.quote("NormalizedEmail")
              + " LIKE ?)";
      String like = query.likeContains().toUpperCase();
      args = new Object[] {like, like};
    }
    int total = Jdbc.queryForInt(dataSource, "SELECT COUNT(*) FROM " + table + where, args);
    String sql =
        dialect.selectPaged(
            columns(),
            "FROM " + table + where,
            dialect.quote("NormalizedUserName"),
            query.offset(),
            query.pageSize());
    List<IdentityUser> items = Jdbc.query(dataSource, sql, UserRepository::mapRow, args);
    return new PageResult<>(query.page(), query.pageSize(), total, items);
  }

  public String insert(
      String userName, String email, boolean emailConfirmed, String passwordHash) {
    String id = java.util.UUID.randomUUID().toString();
    String sql =
        "INSERT INTO "
            + dialect.quote(tables.users())
            + " ("
            + dialect.quote("Id")
            + ", "
            + dialect.quote("UserName")
            + ", "
            + dialect.quote("NormalizedUserName")
            + ", "
            + dialect.quote("Email")
            + ", "
            + dialect.quote("NormalizedEmail")
            + ", "
            + dialect.quote("EmailConfirmed")
            + ", "
            + dialect.quote("PasswordHash")
            + ", "
            + dialect.quote("SecurityStamp")
            + ", "
            + dialect.quote("ConcurrencyStamp")
            + ", "
            + dialect.quote("PhoneNumberConfirmed")
            + ", "
            + dialect.quote("TwoFactorEnabled")
            + ", "
            + dialect.quote("LockoutEnabled")
            + ", "
            + dialect.quote("AccessFailedCount")
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
    String user = userName.trim();
    String mail = email == null ? "" : email.trim();
    Jdbc.execute(
        dataSource,
        sql,
        id,
        user,
        user.toUpperCase(java.util.Locale.ROOT),
        mail,
        mail.toUpperCase(java.util.Locale.ROOT),
        emailConfirmed,
        passwordHash,
        java.util.UUID.randomUUID().toString(),
        java.util.UUID.randomUUID().toString(),
        false,
        false,
        false,
        0);
    return id;
  }

  /** Full Skoruba-style profile update (phone, 2FA, lockout end, access failures). */
  public void update(String id, UserProfileWrite write) {
    String user = write.userName() == null ? "" : write.userName().trim();
    String mail = write.email() == null ? "" : write.email().trim();
    String phone = write.phoneNumber() == null ? "" : write.phoneNumber().trim();
    String sql =
        "UPDATE "
            + dialect.quote(tables.users())
            + " SET "
            + dialect.quote("UserName")
            + " = ?, "
            + dialect.quote("NormalizedUserName")
            + " = ?, "
            + dialect.quote("Email")
            + " = ?, "
            + dialect.quote("NormalizedEmail")
            + " = ?, "
            + dialect.quote("EmailConfirmed")
            + " = ?, "
            + dialect.quote("PhoneNumber")
            + " = ?, "
            + dialect.quote("PhoneNumberConfirmed")
            + " = ?, "
            + dialect.quote("LockoutEnabled")
            + " = ?, "
            + dialect.quote("LockoutEnd")
            + " = ?, "
            + dialect.quote("AccessFailedCount")
            + " = ?, "
            + dialect.quote("TwoFactorEnabled")
            + " = ? WHERE "
            + dialect.quote("Id")
            + " = ?";
    Timestamp lockout =
        write.lockoutEnd() == null ? null : Timestamp.from(write.lockoutEnd());
    Jdbc.execute(
        dataSource,
        sql,
        user,
        user.toUpperCase(java.util.Locale.ROOT),
        mail,
        mail.toUpperCase(java.util.Locale.ROOT),
        write.emailConfirmed(),
        phone.isEmpty() ? null : phone,
        write.phoneNumberConfirmed(),
        write.lockoutEnabled(),
        lockout,
        Math.max(0, write.accessFailedCount()),
        write.twoFactorEnabled(),
        id);
  }

  public void setPasswordHash(String id, String passwordHash) {
    String sql =
        "UPDATE "
            + dialect.quote(tables.users())
            + " SET "
            + dialect.quote("PasswordHash")
            + " = ?, "
            + dialect.quote("SecurityStamp")
            + " = ? WHERE "
            + dialect.quote("Id")
            + " = ?";
    Jdbc.execute(dataSource, sql, passwordHash, java.util.UUID.randomUUID().toString(), id);
  }

  public void delete(String id) {
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userRoles())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ?",
        id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userClaims())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ?",
        id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userLogins())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ?",
        id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userTokens())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ?",
        id);
    Jdbc.execute(
        dataSource,
        "DELETE FROM " + dialect.quote(tables.users()) + " WHERE " + dialect.quote("Id") + " = ?",
        id);
  }

  public void addRole(String userId, String roleId) {
    for (IdentityRole existing : listRoles(userId)) {
      if (existing.id().equals(roleId)) {
        return;
      }
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(tables.userRoles())
            + " ("
            + dialect.quote("UserId")
            + ", "
            + dialect.quote("RoleId")
            + ") VALUES (?, ?)";
    Jdbc.execute(dataSource, sql, userId, roleId);
  }

  public int removeRole(String userId, String roleId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userRoles())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ? AND "
            + dialect.quote("RoleId")
            + " = ?",
        userId,
        roleId);
  }

  public List<IdentityRole> listRoles(String userId) {
    String sql =
        "SELECT r."
            + dialect.quote("Id")
            + ", r."
            + dialect.quote("Name")
            + ", r."
            + dialect.quote("NormalizedName")
            + " FROM "
            + dialect.quote(tables.userRoles())
            + " ur INNER JOIN "
            + dialect.quote(tables.roles())
            + " r ON r."
            + dialect.quote("Id")
            + " = ur."
            + dialect.quote("RoleId")
            + " WHERE ur."
            + dialect.quote("UserId")
            + " = ? ORDER BY r."
            + dialect.quote("Name");
    return Jdbc.query(
        dataSource,
        sql,
        rs -> new IdentityRole(rs.getString("Id"), rs.getString("Name"), rs.getString("NormalizedName")),
        userId);
  }

  public List<UserClaim> listClaims(String userId) {
    String sql =
        "SELECT "
            + dialect.quote("Id")
            + ", "
            + dialect.quote("ClaimType")
            + ", "
            + dialect.quote("ClaimValue")
            + " FROM "
            + dialect.quote(tables.userClaims())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ?";
    return Jdbc.query(
        dataSource,
        sql,
        rs -> new UserClaim(rs.getInt("Id"), rs.getString("ClaimType"), rs.getString("ClaimValue")),
        userId);
  }

  public int addClaim(String userId, String type, String value) {
    if (type == null || type.isBlank()) {
      return 0;
    }
    String sql =
        "INSERT INTO "
            + dialect.quote(tables.userClaims())
            + " ("
            + dialect.quote("UserId")
            + ", "
            + dialect.quote("ClaimType")
            + ", "
            + dialect.quote("ClaimValue")
            + ") VALUES (?, ?, ?)";
    return Jdbc.insertReturningId(dataSource, sql, userId, type.trim(), value == null ? "" : value);
  }

  public int deleteClaim(String userId, int claimId) {
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userClaims())
            + " WHERE "
            + dialect.quote("Id")
            + " = ? AND "
            + dialect.quote("UserId")
            + " = ?",
        claimId,
        userId);
  }

  public List<UserLogin> listLogins(String userId) {
    String sql =
        "SELECT "
            + dialect.quote("LoginProvider")
            + ", "
            + dialect.quote("ProviderKey")
            + ", "
            + dialect.quote("ProviderDisplayName")
            + " FROM "
            + dialect.quote(tables.userLogins())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ? ORDER BY "
            + dialect.quote("LoginProvider");
    return Jdbc.query(
        dataSource,
        sql,
        rs ->
            new UserLogin(
                rs.getString("LoginProvider"),
                rs.getString("ProviderKey"),
                rs.getString("ProviderDisplayName")),
        userId);
  }

  /**
   * Match {@code PhoneNumber} exactly, or digits-only equality (e.g. {@code +8529…} vs {@code
   * 8529…}).
   */
  public Optional<IdentityUser> findByPhoneNumber(String phone) {
    if (phone == null || phone.isBlank()) {
      return Optional.empty();
    }
    String trimmed = phone.trim();
    String sql =
        "SELECT "
            + columns()
            + " FROM "
            + dialect.quote(tables.users())
            + " WHERE "
            + dialect.quote("PhoneNumber")
            + " = ?";
    List<IdentityUser> exact = Jdbc.query(dataSource, sql, UserRepository::mapRow, trimmed);
    if (!exact.isEmpty()) {
      return exact.stream().findFirst();
    }
    String wantDigits = trimmed.replaceAll("\\D", "");
    if (wantDigits.isEmpty()) {
      return Optional.empty();
    }
    String scan =
        "SELECT "
            + columns()
            + " FROM "
            + dialect.quote(tables.users())
            + " WHERE "
            + dialect.quote("PhoneNumber")
            + " IS NOT NULL AND "
            + dialect.quote("PhoneNumber")
            + " <> ''";
    for (IdentityUser user : Jdbc.query(dataSource, scan, UserRepository::mapRow)) {
      if (user.phoneNumber() == null) {
        continue;
      }
      String have = user.phoneNumber().replaceAll("\\D", "");
      if (wantDigits.equals(have)) {
        return Optional.of(user);
      }
    }
    return Optional.empty();
  }

  public Optional<IdentityUser> findByLogin(String loginProvider, String providerKey) {
    if (loginProvider == null
        || loginProvider.isBlank()
        || providerKey == null
        || providerKey.isBlank()) {
      return Optional.empty();
    }
    String userCols =
        String.join(
            ", ",
            "u." + dialect.quote("Id"),
            "u." + dialect.quote("UserName"),
            "u." + dialect.quote("NormalizedUserName"),
            "u." + dialect.quote("Email"),
            "u." + dialect.quote("NormalizedEmail"),
            "u." + dialect.quote("EmailConfirmed"),
            "u." + dialect.quote("PasswordHash"),
            "u." + dialect.quote("SecurityStamp"),
            "u." + dialect.quote("LockoutEnabled"),
            "u." + dialect.quote("LockoutEnd"),
            "u." + dialect.quote("AccessFailedCount"),
            "u." + dialect.quote("TwoFactorEnabled"),
            "u." + dialect.quote("PhoneNumber"),
            "u." + dialect.quote("PhoneNumberConfirmed"));
    String sql =
        "SELECT "
            + userCols
            + " FROM "
            + dialect.quote(tables.userLogins())
            + " l INNER JOIN "
            + dialect.quote(tables.users())
            + " u ON u."
            + dialect.quote("Id")
            + " = l."
            + dialect.quote("UserId")
            + " WHERE l."
            + dialect.quote("LoginProvider")
            + " = ? AND l."
            + dialect.quote("ProviderKey")
            + " = ?";
    return Jdbc.query(dataSource, sql, UserRepository::mapRow, loginProvider, providerKey).stream()
        .findFirst();
  }

  public void addLogin(
      String userId, String loginProvider, String providerKey, String providerDisplayName) {
    if (userId == null
        || userId.isBlank()
        || loginProvider == null
        || loginProvider.isBlank()
        || providerKey == null
        || providerKey.isBlank()) {
      throw new IllegalArgumentException("userId, loginProvider, and providerKey are required");
    }
    if (findByLogin(loginProvider, providerKey).isPresent()) {
      return;
    }
    Jdbc.execute(
        dataSource,
        "INSERT INTO "
            + dialect.quote(tables.userLogins())
            + " ("
            + dialect.quote("LoginProvider")
            + ", "
            + dialect.quote("ProviderKey")
            + ", "
            + dialect.quote("ProviderDisplayName")
            + ", "
            + dialect.quote("UserId")
            + ") VALUES (?,?,?,?)",
        loginProvider.trim(),
        providerKey.trim(),
        providerDisplayName == null || providerDisplayName.isBlank()
            ? loginProvider.trim()
            : providerDisplayName.trim(),
        userId.trim());
  }

  public int deleteLogin(String userId, String loginProvider, String providerKey) {
    if (loginProvider == null || loginProvider.isBlank() || providerKey == null || providerKey.isBlank()) {
      return 0;
    }
    return Jdbc.execute(
        dataSource,
        "DELETE FROM "
            + dialect.quote(tables.userLogins())
            + " WHERE "
            + dialect.quote("UserId")
            + " = ? AND "
            + dialect.quote("LoginProvider")
            + " = ? AND "
            + dialect.quote("ProviderKey")
            + " = ?",
        userId,
        loginProvider,
        providerKey);
  }

  private static IdentityUser mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
    Timestamp lockout = rs.getTimestamp("LockoutEnd");
    Instant lockoutEnd = lockout == null || rs.wasNull() ? null : lockout.toInstant();
    return new IdentityUser(
        rs.getString("Id"),
        rs.getString("UserName"),
        rs.getString("NormalizedUserName"),
        rs.getString("Email"),
        rs.getString("NormalizedEmail"),
        rs.getBoolean("EmailConfirmed"),
        rs.getString("PasswordHash"),
        rs.getString("SecurityStamp"),
        rs.getBoolean("LockoutEnabled"),
        lockoutEnd,
        rs.getInt("AccessFailedCount"),
        rs.getBoolean("TwoFactorEnabled"),
        rs.getString("PhoneNumber"),
        rs.getBoolean("PhoneNumberConfirmed"));
  }
}
