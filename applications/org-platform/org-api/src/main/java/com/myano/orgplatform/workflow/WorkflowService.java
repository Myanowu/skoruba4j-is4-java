package com.myano.orgplatform.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myano.orgplatform.org.MembershipRepository;
import com.myano.orgplatform.org.OrgModels.MembershipDto;
import com.myano.orgplatform.org.OrgModels.OrgRoleDto;
import com.myano.orgplatform.org.OrgRoleRepository;
import com.myano.orgplatform.org.OrgUnitRepository;
import com.myano.orgplatform.workflow.WorkflowModels.ApproverSetDto;
import com.myano.orgplatform.workflow.WorkflowModels.ApproverStep;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessDefinitionDto;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessInstanceDto;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessNode;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessTaskDto;
import com.myano.orgplatform.workflow.WorkflowModels.PublishProcessRequest;
import com.myano.orgplatform.workflow.WorkflowModels.TaskActionRequest;
import com.myano.orgplatform.workflow.WorkflowModels.UpsertApproverSetRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

  private static final RowMapper<ProcessDefinitionDto> DEF_MAPPER =
      (rs, i) ->
          new ProcessDefinitionDto(
              rs.getLong("id"),
              rs.getString("code"),
              rs.getInt("version"),
              rs.getString("title"),
              rs.getString("nodes_json"),
              rs.getString("bound_form_code"),
              rs.getString("status"));

  private static final RowMapper<ProcessInstanceDto> INST_MAPPER =
      (rs, i) ->
          new ProcessInstanceDto(
              rs.getLong("id"),
              rs.getString("process_code"),
              rs.getLong("form_instance_id"),
              rs.getString("status"),
              rs.getString("current_node_key"),
              rs.getString("starter_user_id"),
              rs.getObject("org_unit_id") == null ? null : rs.getLong("org_unit_id"),
              rs.getObject("amount") == null ? null : rs.getDouble("amount"));

  private static final RowMapper<ProcessTaskDto> TASK_MAPPER =
      (rs, i) ->
          new ProcessTaskDto(
              rs.getLong("id"),
              rs.getLong("process_instance_id"),
              rs.getString("node_key"),
              rs.getString("candidate_user_ids"),
              rs.getString("candidate_group"),
              rs.getString("status"),
              rs.getString("action"),
              rs.getString("actioned_by"),
              rs.getString("remarks"),
              rs.getString("created_at"));

  private static final RowMapper<ApproverSetDto> SET_MAPPER =
      (rs, i) ->
          new ApproverSetDto(
              rs.getLong("id"),
              rs.getObject("org_unit_id") == null ? null : rs.getLong("org_unit_id"),
              rs.getString("code"),
              rs.getString("title"),
              rs.getString("kind"),
              rs.getString("steps_json"));

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final OrgUnitRepository units;
  private final OrgRoleRepository roles;
  private final MembershipRepository memberships;

  public WorkflowService(
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      OrgUnitRepository units,
      OrgRoleRepository roles,
      MembershipRepository memberships) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.units = units;
    this.roles = roles;
    this.memberships = memberships;
  }

  @Transactional
  public ProcessDefinitionDto publishProcess(PublishProcessRequest req) {
    Integer max =
        jdbc.query(
            "SELECT MAX(version) FROM process_definition WHERE code = ?",
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
                  INSERT INTO process_definition
                    (code, version, title, nodes_json, bound_form_code, status)
                  VALUES (?,?,?,?,?,'PUBLISHED')
                  """,
                  new String[] {"id"});
          ps.setString(1, req.code().trim());
          ps.setInt(2, version);
          ps.setString(3, req.title());
          ps.setString(4, req.nodesJson());
          ps.setString(5, req.boundFormCode());
          return ps;
        },
        keys);
    long id = keys.getKey().longValue();
    return jdbc.query("SELECT * FROM process_definition WHERE id = ?", DEF_MAPPER, id).getFirst();
  }

  public Optional<ProcessDefinitionDto> latestPublished(String code) {
    List<ProcessDefinitionDto> list =
        jdbc.query(
            """
            SELECT * FROM process_definition WHERE code = ? AND status = 'PUBLISHED'
            ORDER BY version DESC LIMIT 1
            """,
            DEF_MAPPER,
            code);
    return list.stream().findFirst();
  }

  @Transactional
  public ApproverSetDto upsertApproverSet(UpsertApproverSetRequest req) {
    jdbc.update("DELETE FROM approver_set_template WHERE code = ?", req.code());
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
                  INSERT INTO approver_set_template (org_unit_id, code, title, kind, steps_json)
                  VALUES (?,?,?,?,?)
                  """,
                  new String[] {"id"});
          if (req.orgUnitId() == null) {
            ps.setObject(1, null);
          } else {
            ps.setLong(1, req.orgUnitId());
          }
          ps.setString(2, req.code());
          ps.setString(3, req.title());
          ps.setString(4, req.kind() == null ? "GENERIC" : req.kind());
          ps.setString(5, req.stepsJson());
          return ps;
        },
        keys);
    long id = keys.getKey().longValue();
    return jdbc.query("SELECT * FROM approver_set_template WHERE id = ?", SET_MAPPER, id).getFirst();
  }

  public List<ApproverSetDto> listApproverSets() {
    return jdbc.query("SELECT * FROM approver_set_template ORDER BY code", SET_MAPPER);
  }

  @Transactional
  public ProcessInstanceDto startProcess(
      String processCode, long formInstanceId, String starterUserId, Long orgUnitId, String payloadJson) {
    ProcessDefinitionDto def =
        latestPublished(processCode)
            .orElseThrow(() -> new IllegalArgumentException("process not published: " + processCode));
    List<ProcessNode> nodes = parseNodes(def.nodesJson());
    if (nodes.isEmpty()) {
      throw new IllegalStateException("process has no nodes");
    }
    Double amount = extractAmount(payloadJson);
    List<ProcessNode> effective = applyApproverSetAndThreshold(nodes, orgUnitId, amount);
    if (effective.isEmpty()) {
      // no approval steps → auto approve
      KeyHolder keys = new GeneratedKeyHolder();
      jdbc.update(
          con -> {
            var ps =
                con.prepareStatement(
                    """
                    INSERT INTO process_instance
                      (process_def_id, process_code, form_instance_id, status, current_node_key,
                       starter_user_id, org_unit_id, amount)
                    VALUES (?,?,?,'APPROVED',NULL,?,?,?)
                    """,
                    new String[] {"id"});
            ps.setLong(1, def.id());
            ps.setString(2, def.code());
            ps.setLong(3, formInstanceId);
            ps.setString(4, starterUserId);
            if (orgUnitId == null) {
              ps.setObject(5, null);
            } else {
              ps.setLong(5, orgUnitId);
            }
            if (amount == null) {
              ps.setObject(6, null);
            } else {
              ps.setDouble(6, amount);
            }
            return ps;
          },
          keys);
      jdbc.update(
          "UPDATE form_instance SET status = 'APPROVED', updated_at = datetime('now') WHERE id = ?",
          formInstanceId);
      return jdbc
          .query("SELECT * FROM process_instance WHERE id = ?", INST_MAPPER, keys.getKey().longValue())
          .getFirst();
    }

    ProcessNode first = effective.getFirst();
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  """
                  INSERT INTO process_instance
                    (process_def_id, process_code, form_instance_id, status, current_node_key,
                     starter_user_id, org_unit_id, amount)
                  VALUES (?,?,?,'RUNNING',?,?,?,?)
                  """,
                  new String[] {"id"});
          ps.setLong(1, def.id());
          ps.setString(2, def.code());
          ps.setLong(3, formInstanceId);
          ps.setString(4, first.key());
          ps.setString(5, starterUserId);
          if (orgUnitId == null) {
            ps.setObject(6, null);
          } else {
            ps.setLong(6, orgUnitId);
          }
          if (amount == null) {
            ps.setObject(7, null);
          } else {
            ps.setDouble(7, amount);
          }
          return ps;
        },
        keys);
    long piId = keys.getKey().longValue();
    openTask(piId, first, orgUnitId, starterUserId);
    // store effective node sequence on instance via rewriting current path — keep full list in memory by
    // persisting nodes_json snapshot? For MVP re-parse def and re-apply threshold on each step.
    return jdbc.query("SELECT * FROM process_instance WHERE id = ?", INST_MAPPER, piId).getFirst();
  }

  private List<ProcessNode> applyApproverSetAndThreshold(
      List<ProcessNode> nodes, Long orgUnitId, Double amount) {
    List<ProcessNode> out = new ArrayList<>();
    for (ProcessNode n : nodes) {
      if ("APPROVER_SET".equalsIgnoreCase(n.resolveStrategy()) && n.approverSetCode() != null) {
        Optional<ApproverSetDto> set =
            jdbc
                .query(
                    "SELECT * FROM approver_set_template WHERE code = ?",
                    SET_MAPPER,
                    n.approverSetCode())
                .stream()
                .findFirst();
        if (set.isPresent()) {
          List<ApproverStep> steps = parseSteps(set.get().stepsJson());
          steps.sort(Comparator.comparingInt(ApproverStep::order));
          for (ApproverStep s : steps) {
            if (amount != null && s.threshold() != null && amount < s.threshold()) {
              continue; // skip step when amount below threshold (ELCSS-style)
            }
            out.add(
                new ProcessNode(
                    n.key() + "_" + s.order(),
                    n.name() + "#" + s.order(),
                    s.resolveStrategy() == null ? "ORG_ROLE" : s.resolveStrategy(),
                    s.orgRoleCode(),
                    null,
                    null,
                    s.threshold()));
          }
          continue;
        }
      }
      if (amount != null && n.threshold() != null && amount < n.threshold()) {
        continue;
      }
      out.add(n);
    }
    return out;
  }

  private void openTask(long processInstanceId, ProcessNode node, Long orgUnitId, String starterUserId) {
    String candidates = resolveCandidates(node, orgUnitId, starterUserId);
    jdbc.update(
        """
        INSERT INTO process_task
          (process_instance_id, node_key, candidate_user_ids, candidate_group, status)
        VALUES (?,?,?,?,'OPEN')
        """,
        processInstanceId,
        node.key(),
        candidates,
        node.orgRoleCode());
  }

  private String resolveCandidates(ProcessNode node, Long orgUnitId, String starterUserId) {
    String strategy = node.resolveStrategy() == null ? "MANAGER_CHAIN" : node.resolveStrategy();
    return switch (strategy.toUpperCase()) {
      case "FIXED_USER" -> node.fixedUserId() == null ? "" : node.fixedUserId();
      case "ORG_ROLE" -> {
        if (orgUnitId == null || node.orgRoleCode() == null) {
          yield starterUserId;
        }
        List<OrgRoleDto> unitRoles = roles.findByUnit(orgUnitId);
        Optional<OrgRoleDto> role =
            unitRoles.stream()
                .filter(r -> r.code().equalsIgnoreCase(node.orgRoleCode()))
                .findFirst();
        if (role.isEmpty()) {
          yield starterUserId;
        }
        yield memberships.findActiveByRole(role.get().id()).stream()
            .map(MembershipDto::userId)
            .distinct()
            .collect(Collectors.joining(","));
      }
      case "MANAGER_CHAIN" -> {
        if (orgUnitId == null) {
          yield starterUserId;
        }
        yield units
            .findById(orgUnitId)
            .map(u -> u.managerUserId() != null ? u.managerUserId() : starterUserId)
            .orElse(starterUserId);
      }
      default -> starterUserId;
    };
  }

  @Transactional
  public ProcessTaskDto act(long taskId, TaskActionRequest req) {
    ProcessTaskDto task =
        jdbc.query("SELECT * FROM process_task WHERE id = ?", TASK_MAPPER, taskId).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("task not found"));
    if (!"OPEN".equals(task.status())) {
      throw new IllegalStateException("task not open");
    }
    String action = req.action() == null ? "" : req.action().toUpperCase();
    if (!List.of("APPROVE", "REJECT", "RECALL").contains(action)) {
      throw new IllegalArgumentException("action must be APPROVE|REJECT|RECALL");
    }
    jdbc.update(
        """
        UPDATE process_task SET status = 'CLOSED', action = ?, actioned_by = ?, remarks = ?,
          closed_at = datetime('now') WHERE id = ?
        """,
        action,
        req.userId(),
        req.remarks(),
        taskId);

    ProcessInstanceDto pi =
        jdbc.query("SELECT * FROM process_instance WHERE id = ?", INST_MAPPER, task.processInstanceId())
            .getFirst();
    ProcessDefinitionDto def =
        latestPublished(pi.processCode())
            .orElseThrow(() -> new IllegalStateException("process definition missing"));

    if ("REJECT".equals(action) || "RECALL".equals(action)) {
      String status = "REJECT".equals(action) ? "REJECTED" : "RECALLED";
      jdbc.update(
          "UPDATE process_instance SET status = ?, current_node_key = NULL, updated_at = datetime('now') WHERE id = ?",
          status,
          pi.id());
      jdbc.update(
          "UPDATE form_instance SET status = ?, updated_at = datetime('now') WHERE id = ?",
          status,
          pi.formInstanceId());
    } else {
      List<ProcessNode> effective =
          applyApproverSetAndThreshold(parseNodes(def.nodesJson()), pi.orgUnitId(), pi.amount());
      int idx = -1;
      for (int i = 0; i < effective.size(); i++) {
        if (effective.get(i).key().equals(task.nodeKey())) {
          idx = i;
          break;
        }
      }
      if (idx < 0 || idx >= effective.size() - 1) {
        jdbc.update(
            "UPDATE process_instance SET status = 'APPROVED', current_node_key = NULL, updated_at = datetime('now') WHERE id = ?",
            pi.id());
        jdbc.update(
            "UPDATE form_instance SET status = 'APPROVED', updated_at = datetime('now') WHERE id = ?",
            pi.formInstanceId());
      } else {
        ProcessNode next = effective.get(idx + 1);
        jdbc.update(
            "UPDATE process_instance SET current_node_key = ?, updated_at = datetime('now') WHERE id = ?",
            next.key(),
            pi.id());
        openTask(pi.id(), next, pi.orgUnitId(), pi.starterUserId());
      }
    }

    return jdbc.query("SELECT * FROM process_task WHERE id = ?", TASK_MAPPER, taskId).getFirst();
  }

  public List<ProcessTaskDto> inbox(String userId) {
    return jdbc.query(
        """
        SELECT * FROM process_task
        WHERE status = 'OPEN'
          AND (',' || IFNULL(candidate_user_ids,'') || ',') LIKE '%,' || ? || ',%'
        ORDER BY id DESC
        """,
        TASK_MAPPER,
        userId);
  }

  public List<ProcessTaskDto> done(String userId) {
    return jdbc.query(
        "SELECT * FROM process_task WHERE status = 'CLOSED' AND actioned_by = ? ORDER BY id DESC",
        TASK_MAPPER,
        userId);
  }

  private List<ProcessNode> parseNodes(String json) {
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid nodes_json: " + e.getMessage());
    }
  }

  private List<ApproverStep> parseSteps(String json) {
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid steps_json: " + e.getMessage());
    }
  }

  private Double extractAmount(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return null;
    }
    try {
      JsonNode n = mapper.readTree(payloadJson);
      if (n.has("amount") && n.get("amount").isNumber()) {
        return n.get("amount").asDouble();
      }
    } catch (Exception ignored) {
      // ignore
    }
    return null;
  }
}
