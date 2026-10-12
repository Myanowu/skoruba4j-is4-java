package com.myano.orgplatform.form;

import com.myano.orgplatform.form.FormModels.FormDefinitionDto;
import com.myano.orgplatform.form.FormModels.FormInstanceDto;
import com.myano.orgplatform.form.FormModels.PublishFormRequest;
import com.myano.orgplatform.form.FormModels.SubmitFormRequest;
import com.myano.orgplatform.workflow.WorkflowService;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormService {

  private static final RowMapper<FormDefinitionDto> DEF_MAPPER =
      (rs, i) ->
          new FormDefinitionDto(
              rs.getLong("id"),
              rs.getString("code"),
              rs.getInt("version"),
              rs.getString("title"),
              rs.getString("schema_json"),
              rs.getString("list_fields_json"),
              rs.getString("status"),
              rs.getString("process_def_code"));

  private static final RowMapper<FormInstanceDto> INST_MAPPER =
      (rs, i) ->
          new FormInstanceDto(
              rs.getLong("id"),
              rs.getLong("form_def_id"),
              rs.getString("form_code"),
              rs.getInt("form_version"),
              rs.getObject("org_unit_id") == null ? null : rs.getLong("org_unit_id"),
              rs.getString("created_by"),
              rs.getString("status"),
              rs.getString("payload_json"),
              rs.getString("created_at"));

  private final JdbcTemplate jdbc;
  private final WorkflowService workflowService;

  public FormService(JdbcTemplate jdbc, @Lazy WorkflowService workflowService) {
    this.jdbc = jdbc;
    this.workflowService = workflowService;
  }

  public List<FormDefinitionDto> listPublished() {
    return jdbc.query(
        "SELECT * FROM form_definition WHERE status = 'PUBLISHED' ORDER BY code, version DESC",
        DEF_MAPPER);
  }

  public Optional<FormDefinitionDto> latestPublished(String code) {
    List<FormDefinitionDto> list =
        jdbc.query(
            """
            SELECT * FROM form_definition
            WHERE code = ? AND status = 'PUBLISHED'
            ORDER BY version DESC LIMIT 1
            """,
            DEF_MAPPER,
            code);
    return list.stream().findFirst();
  }

  @Transactional
  public FormDefinitionDto publish(PublishFormRequest req) {
    Integer max =
        jdbc.query(
            "SELECT MAX(version) FROM form_definition WHERE code = ?",
            rs -> {
              if (!rs.next()) {
                return null;
              }
              Object v = rs.getObject(1);
              return v == null ? null : ((Number) v).intValue();
            },
            req.code());
    int version = (max == null ? 0 : max) + 1;
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
                  INSERT INTO form_definition
                    (code, version, title, schema_json, list_fields_json, status, process_def_code)
                  VALUES (?,?,?,?,?,'PUBLISHED',?)
                  """,
                  new String[] {"id"});
          ps.setString(1, req.code().trim());
          ps.setInt(2, version);
          ps.setString(3, req.title());
          ps.setString(4, req.schemaJson());
          ps.setString(5, req.listFieldsJson());
          ps.setString(6, req.processDefCode());
          return ps;
        },
        keys);
    long id = keys.getKey().longValue();
    return jdbc.query("SELECT * FROM form_definition WHERE id = ?", DEF_MAPPER, id).getFirst();
  }

  @Transactional
  public FormInstanceDto submit(SubmitFormRequest req) {
    FormDefinitionDto def =
        latestPublished(req.formCode())
            .orElseThrow(() -> new IllegalArgumentException("form not published: " + req.formCode()));
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
                  INSERT INTO form_instance
                    (form_def_id, form_code, form_version, org_unit_id, created_by, status, payload_json)
                  VALUES (?,?,?,?,?,'SUBMITTED',?)
                  """,
                  new String[] {"id"});
          ps.setLong(1, def.id());
          ps.setString(2, def.code());
          ps.setInt(3, def.version());
          if (req.orgUnitId() == null) {
            ps.setObject(4, null);
          } else {
            ps.setLong(4, req.orgUnitId());
          }
          ps.setString(5, req.userId());
          ps.setString(6, req.payloadJson() == null ? "{}" : req.payloadJson());
          return ps;
        },
        keys);
    long instanceId = keys.getKey().longValue();
    if (def.processDefCode() != null && !def.processDefCode().isBlank()) {
      workflowService.startProcess(
          def.processDefCode(), instanceId, req.userId(), req.orgUnitId(), req.payloadJson());
    }
    return jdbc.query("SELECT * FROM form_instance WHERE id = ?", INST_MAPPER, instanceId).getFirst();
  }

  public List<FormInstanceDto> mine(String userId) {
    return jdbc.query(
        "SELECT * FROM form_instance WHERE created_by = ? ORDER BY id DESC", INST_MAPPER, userId);
  }

  public List<FormInstanceDto> byOrgPathPrefix(String pathPrefix) {
    return jdbc.query(
        """
        SELECT fi.* FROM form_instance fi
        JOIN org_unit ou ON ou.id = fi.org_unit_id
        WHERE ou.path LIKE ? || '%'
        ORDER BY fi.id DESC
        """,
        INST_MAPPER,
        pathPrefix);
  }

  public FormInstanceDto get(long id) {
    List<FormInstanceDto> list =
        jdbc.query("SELECT * FROM form_instance WHERE id = ?", INST_MAPPER, id);
    if (list.isEmpty()) {
      throw new IllegalArgumentException("form instance not found");
    }
    return list.getFirst();
  }
}
