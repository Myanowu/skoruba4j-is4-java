package com.myano.orgplatform.org;

import com.myano.orgplatform.org.OrgModels.OrgRoleDto;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrgRoleRepository {

  private static final RowMapper<OrgRoleDto> MAPPER =
      (rs, i) ->
          new OrgRoleDto(
              rs.getLong("id"), rs.getLong("org_unit_id"), rs.getString("code"), rs.getString("name"));

  private final JdbcTemplate jdbc;

  public OrgRoleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<OrgRoleDto> findByUnit(long orgUnitId) {
    return jdbc.query("SELECT * FROM org_role WHERE org_unit_id = ? ORDER BY code", MAPPER, orgUnitId);
  }

  public Optional<OrgRoleDto> findById(long id) {
    List<OrgRoleDto> list = jdbc.query("SELECT * FROM org_role WHERE id = ?", MAPPER, id);
    return list.stream().findFirst();
  }

  public long insert(long orgUnitId, String code, String name) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  "INSERT INTO org_role (org_unit_id, code, name) VALUES (?,?,?)", new String[] {"id"});
          ps.setLong(1, orgUnitId);
          ps.setString(2, code);
          ps.setString(3, name);
          return ps;
        },
        keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to insert org_role");
    }
    return key.longValue();
  }

  /** Renames a position without changing its code or department. */
  public void updateName(long id, String name) {
    jdbc.update("UPDATE org_role SET name = ? WHERE id = ?", name, id);
  }

  public void grantPermission(long orgRoleId, String permissionCode) {
    jdbc.update(
        "INSERT OR IGNORE INTO role_permission (org_role_id, permission_code) VALUES (?,?)",
        orgRoleId,
        permissionCode);
  }

  public List<String> permissionsForRole(long orgRoleId) {
    return jdbc.query(
        "SELECT permission_code FROM role_permission WHERE org_role_id = ?",
        (rs, i) -> rs.getString(1),
        orgRoleId);
  }
}
