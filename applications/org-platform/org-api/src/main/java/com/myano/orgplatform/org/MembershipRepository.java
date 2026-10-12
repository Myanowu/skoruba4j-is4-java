package com.myano.orgplatform.org;

import com.myano.orgplatform.org.OrgModels.MembershipDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {

  private static final RowMapper<MembershipDto> MAPPER =
      (rs, i) ->
          new MembershipDto(
              rs.getLong("id"),
              rs.getString("user_id"),
              rs.getObject("org_unit_id") == null ? null : rs.getLong("org_unit_id"),
              rs.getObject("org_role_id") == null ? null : rs.getLong("org_role_id"),
              rs.getString("title"),
              rs.getInt("is_primary") == 1,
              rs.getString("effective_from"),
              rs.getString("effective_to"));

  private final JdbcTemplate jdbc;

  public MembershipRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<MembershipDto> findByUser(String userId) {
    return jdbc.query(
        "SELECT * FROM org_membership WHERE user_id = ? ORDER BY is_primary DESC, id", MAPPER, userId);
  }

  public List<MembershipDto> findActiveByUnit(long orgUnitId) {
    return jdbc.query(
        """
        SELECT * FROM org_membership
        WHERE org_unit_id = ?
          AND (effective_to IS NULL OR effective_to > datetime('now'))
        ORDER BY id
        """,
        MAPPER,
        orgUnitId);
  }

  public List<MembershipDto> findActiveByRole(long orgRoleId) {
    return jdbc.query(
        """
        SELECT * FROM org_membership
        WHERE org_role_id = ?
          AND (effective_to IS NULL OR effective_to > datetime('now'))
        """,
        MAPPER,
        orgRoleId);
  }

  public Optional<MembershipDto> findPrimary(String userId) {
    List<MembershipDto> list =
        jdbc.query(
            """
            SELECT * FROM org_membership
            WHERE user_id = ? AND is_primary = 1
              AND (effective_to IS NULL OR effective_to > datetime('now'))
            LIMIT 1
            """,
            MAPPER,
            userId);
    return list.stream().findFirst();
  }

  public long insert(
      String userId,
      Long orgUnitId,
      Long orgRoleId,
      String title,
      boolean primary,
      String effectiveFrom,
      String effectiveTo) {
    if (primary) {
      jdbc.update("UPDATE org_membership SET is_primary = 0 WHERE user_id = ?", userId);
    }
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
                  INSERT INTO org_membership
                    (user_id, org_unit_id, org_role_id, title, is_primary, effective_from, effective_to)
                  VALUES (?,?,?,?,?,?,?)
                  """,
                  new String[] {"id"});
          ps.setString(1, userId);
          if (orgUnitId == null) {
            ps.setObject(2, null);
          } else {
            ps.setLong(2, orgUnitId);
          }
          if (orgRoleId == null) {
            ps.setObject(3, null);
          } else {
            ps.setLong(3, orgRoleId);
          }
          ps.setString(4, title);
          ps.setInt(5, primary ? 1 : 0);
          ps.setString(6, effectiveFrom != null ? effectiveFrom : java.time.Instant.now().toString());
          ps.setString(7, effectiveTo);
          return ps;
        },
        keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to insert membership");
    }
    return key.longValue();
  }

  /** Loads one assignment, including rows that have already ended. */
  public Optional<MembershipDto> findById(long id) {
    List<MembershipDto> list = jdbc.query("SELECT * FROM org_membership WHERE id = ?", MAPPER, id);
    return list.stream().findFirst();
  }

  /** Active headcount grouped by department. Closed assignments are excluded. */
  public Map<Long, Integer> countActiveByUnit() {
    return jdbc.query(
        """
        SELECT org_unit_id, COUNT(*)
        FROM org_membership
        WHERE org_unit_id IS NOT NULL
          AND (effective_to IS NULL OR effective_to > datetime('now'))
        GROUP BY org_unit_id
        """,
        rs -> {
          Map<Long, Integer> counts = new HashMap<>();
          while (rs.next()) {
            counts.put(rs.getLong(1), rs.getInt(2));
          }
          return counts;
        });
  }

  /** Closes every still-open assignment for this user key. */
  public void closeActiveForUser(String userId) {
    jdbc.update(
        """
        UPDATE org_membership
        SET effective_to = datetime('now')
        WHERE user_id = ?
          AND (effective_to IS NULL OR effective_to > datetime('now'))
        """,
        userId);
  }

  /** Points an assignment at a position and optionally marks it primary for that user. */
  public void updateAssignment(long id, String userId, Long orgRoleId, String title, boolean primary) {
    if (primary) {
      jdbc.update("UPDATE org_membership SET is_primary = 0 WHERE user_id = ?", userId);
    }
    jdbc.update(
        "UPDATE org_membership SET org_role_id = ?, title = ?, is_primary = ? WHERE id = ?",
        orgRoleId,
        title,
        primary ? 1 : 0,
        id);
  }
}
