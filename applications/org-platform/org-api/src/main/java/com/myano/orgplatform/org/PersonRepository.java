package com.myano.orgplatform.org;

import com.myano.orgplatform.org.OrgModels.MemberRow;
import com.myano.orgplatform.org.OrgModels.PersonDto;
import com.myano.orgplatform.org.OrgModels.PersonIndex;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PersonRepository {

  private static final RowMapper<PersonDto> MAPPER =
      (rs, i) ->
          new PersonDto(
              rs.getLong("id"),
              rs.getString("user_key"),
              rs.getString("idp_user_id"),
              rs.getString("login_name"),
              rs.getString("employee_no"),
              rs.getString("display_name"),
              rs.getString("email"),
              rs.getString("phone"),
              rs.getString("status"));

  private final JdbcTemplate jdbc;

  public PersonRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Loads a personnel file by its local id. */
  public Optional<PersonDto> findById(long id) {
    return jdbc.query("SELECT * FROM org_person WHERE id = ?", MAPPER, id).stream().findFirst();
  }

  /** Loads a personnel file by the key stored on memberships (later, STS Users.Id). */
  public Optional<PersonDto> findByUserKey(String userKey) {
    return jdbc.query("SELECT * FROM org_person WHERE user_key = ?", MAPPER, userKey).stream()
        .findFirst();
  }

  /** Loads the person already linked to this STS {@code Users.Id}. */
  public Optional<PersonDto> findByIdpUserId(String idpUserId) {
    return jdbc.query("SELECT * FROM org_person WHERE idp_user_id = ?", MAPPER, idpUserId).stream()
        .findFirst();
  }

  /** Loads a personnel file by employee number. */
  public Optional<PersonDto> findByEmployeeNo(String employeeNo) {
    return jdbc.query("SELECT * FROM org_person WHERE employee_no = ?", MAPPER, employeeNo).stream()
        .findFirst();
  }

  /** Counts personnel files, including disabled ones. */
  public int count() {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM org_person", Integer.class);
    return n == null ? 0 : n;
  }

  /**
   * Inserts a personnel file. {@code idpUserId} stays empty until an STS account is linked.
   * Returns the new id.
   */
  public long insert(
      String userKey,
      String idpUserId,
      String employeeNo,
      String displayName,
      String email,
      String phone,
      String status) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
                  INSERT INTO org_person
                    (user_key, idp_user_id, employee_no, display_name, email, phone, status)
                  VALUES (?,?,?,?,?,?,?)
                  """,
                  new String[] {"id"});
          ps.setString(1, userKey);
          ps.setString(2, idpUserId);
          ps.setString(3, employeeNo);
          ps.setString(4, displayName);
          ps.setString(5, email);
          ps.setString(6, phone);
          ps.setString(7, status == null || status.isBlank() ? "ACTIVE" : status);
          return ps;
        },
        keys);
    Number key = keys.getKey();
    if (key == null) {
      throw new IllegalStateException("Failed to insert org_person");
    }
    return key.longValue();
  }

  /**
   * Stores the STS link. {@code loginName} is a display copy of {@code UserName}; the password is
   * never written here.
   */
  public void linkLogin(long id, String idpUserId, String loginName, String email) {
    jdbc.update(
        """
        UPDATE org_person
        SET idp_user_id = ?, login_name = ?, email = ?, updated_at = datetime('now')
        WHERE id = ?
        """,
        idpUserId,
        loginName,
        email,
        id);
  }

  /** Updates HR profile fields. Does not change {@code user_key} or the STS link. */
  public void update(
      long id, String employeeNo, String displayName, String email, String phone, String status) {
    jdbc.update(
        """
        UPDATE org_person
        SET employee_no = ?, display_name = ?, email = ?, phone = ?, status = ?,
            updated_at = datetime('now')
        WHERE id = ?
        """,
        employeeNo,
        displayName,
        email,
        phone,
        status,
        id);
  }

  /** Active people and the department of their primary assignment, for the search box. */
  public List<PersonIndex> listIndex() {
    return jdbc.query(
        """
        SELECT p.id, p.display_name, p.employee_no, p.user_key, p.email,
               m.org_unit_id, u.name AS unit_name
        FROM org_person p
        LEFT JOIN org_membership m
          ON m.user_id = p.user_key AND m.is_primary = 1
         AND (m.effective_to IS NULL OR m.effective_to > datetime('now'))
        LEFT JOIN org_unit u ON u.id = m.org_unit_id
        WHERE p.status = 'ACTIVE'
        ORDER BY p.display_name
        """,
        (rs, i) ->
            new PersonIndex(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("employee_no"),
                rs.getString("user_key"),
                rs.getString("email"),
                rs.getObject("org_unit_id") == null ? null : rs.getLong("org_unit_id"),
                rs.getString("unit_name")));
  }

  /** Open assignments in one department, with person and position names when present. */
  public List<MemberRow> listActiveMembers(long orgUnitId) {
    return jdbc.query(
        """
        SELECT m.id AS membership_id, m.user_id, m.org_role_id, m.title, m.is_primary,
               p.id AS person_id, p.employee_no, p.display_name, p.email, p.phone,
               p.status, p.idp_user_id, p.login_name, r.name AS role_name
        FROM org_membership m
        LEFT JOIN org_person p ON p.user_key = m.user_id
        LEFT JOIN org_role r ON r.id = m.org_role_id
        WHERE m.org_unit_id = ?
          AND (m.effective_to IS NULL OR m.effective_to > datetime('now'))
        ORDER BY m.is_primary DESC, COALESCE(p.display_name, m.user_id)
        """,
        (rs, i) -> {
          String userKey = rs.getString("user_id");
          String display = rs.getString("display_name");
          if (display == null || display.isBlank()) {
            display = userKey;
          }
          String idp = rs.getString("idp_user_id");
          return new MemberRow(
              rs.getLong("membership_id"),
              rs.getObject("person_id") == null ? null : rs.getLong("person_id"),
              userKey,
              display,
              rs.getString("employee_no"),
              rs.getString("email"),
              rs.getString("phone"),
              rs.getString("status") == null ? "ACTIVE" : rs.getString("status"),
              rs.getObject("org_role_id") == null ? null : rs.getLong("org_role_id"),
              rs.getString("role_name"),
              rs.getString("title"),
              rs.getInt("is_primary") == 1,
              rs.getString("login_name"),
              idp != null && !idp.isBlank());
        },
        orgUnitId);
  }
}
