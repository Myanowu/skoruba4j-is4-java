package com.myano.orgplatform.org;

import com.myano.orgplatform.org.OrgModels.OrgUnitDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrgUnitRepository {

  private static final RowMapper<OrgUnitDto> MAPPER =
      (rs, i) ->
          new OrgUnitDto(
              rs.getLong("id"),
              rs.getObject("parent_id") == null ? null : rs.getLong("parent_id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("path"),
              rs.getString("unit_type"),
              rs.getInt("enabled") == 1,
              rs.getString("manager_user_id"),
              List.of());

  private final JdbcTemplate jdbc;

  public OrgUnitRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<OrgUnitDto> findAll() {
    return jdbc.query("SELECT * FROM org_unit ORDER BY path", MAPPER);
  }

  public Optional<OrgUnitDto> findById(long id) {
    List<OrgUnitDto> list = jdbc.query("SELECT * FROM org_unit WHERE id = ?", MAPPER, id);
    return list.stream().findFirst();
  }

  public Optional<OrgUnitDto> findByCode(String code) {
    List<OrgUnitDto> list = jdbc.query("SELECT * FROM org_unit WHERE code = ?", MAPPER, code);
    return list.stream().findFirst();
  }

  public long insert(Long parentId, String code, String name, String path, String unitType, String managerUserId) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  "INSERT INTO org_unit (parent_id, code, name, path, unit_type, manager_user_id) VALUES (?,?,?,?,?,?)",
                  new String[] {"id"});
          if (parentId == null) {
            ps.setObject(1, null);
          } else {
            ps.setLong(1, parentId);
          }
          ps.setString(2, code);
          ps.setString(3, name);
          ps.setString(4, path);
          ps.setString(5, unitType);
          ps.setString(6, managerUserId);
          return ps;
        },
        keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to insert org_unit");
    }
    return key.longValue();
  }

  public void update(long id, String name, String unitType, String managerUserId, boolean enabled) {
    jdbc.update(
        "UPDATE org_unit SET name = ?, unit_type = ?, manager_user_id = ?, enabled = ?, updated_at = datetime('now') WHERE id = ?",
        name,
        unitType,
        managerUserId,
        enabled ? 1 : 0,
        id);
  }

  public void updatePath(long id, String path) {
    jdbc.update("UPDATE org_unit SET path = ?, updated_at = datetime('now') WHERE id = ?", path, id);
  }

  public List<OrgUnitDto> asTree() {
    List<OrgUnitDto> flat = findAll();
    Map<Long, OrgUnitDto> byId = new LinkedHashMap<>();
    for (OrgUnitDto u : flat) {
      byId.put(u.id(), new OrgUnitDto(u.id(), u.parentId(), u.code(), u.name(), u.path(), u.unitType(), u.enabled(), u.managerUserId(), new ArrayList<>()));
    }
    List<OrgUnitDto> roots = new ArrayList<>();
    for (OrgUnitDto u : byId.values()) {
      if (u.parentId() == null || !byId.containsKey(u.parentId())) {
        roots.add(u);
      } else {
        byId.get(u.parentId()).children().add(u);
      }
    }
    return roots;
  }
}
