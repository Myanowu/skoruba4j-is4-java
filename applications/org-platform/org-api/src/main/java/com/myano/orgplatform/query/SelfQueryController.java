package com.myano.orgplatform.query;

import com.myano.orgplatform.form.FormModels.FormInstanceDto;
import com.myano.orgplatform.form.FormService;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessTaskDto;
import com.myano.orgplatform.workflow.WorkflowService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/self")
public class SelfQueryController {

  private final FormService formService;
  private final WorkflowService workflowService;
  private final JdbcTemplate jdbc;

  public SelfQueryController(
      FormService formService, WorkflowService workflowService, JdbcTemplate jdbc) {
    this.formService = formService;
    this.workflowService = workflowService;
    this.jdbc = jdbc;
  }

  @GetMapping("/mine")
  public List<FormInstanceDto> mine(@RequestParam String userId) {
    return formService.mine(userId);
  }

  @GetMapping("/inbox")
  public List<ProcessTaskDto> inbox(@RequestParam String userId) {
    return workflowService.inbox(userId);
  }

  @GetMapping("/done")
  public List<ProcessTaskDto> done(@RequestParam String userId) {
    return workflowService.done(userId);
  }

  @GetMapping("/org-forms")
  public List<FormInstanceDto> orgForms(@RequestParam String orgPathPrefix) {
    return formService.byOrgPathPrefix(orgPathPrefix);
  }

  @PostMapping("/export")
  public Map<String, Object> export(
      @RequestParam String userId,
      @RequestParam(required = false) String formCode,
      @RequestParam(required = false) String orgPathPrefix) {
    List<FormInstanceDto> rows;
    if (orgPathPrefix != null && !orgPathPrefix.isBlank()) {
      rows = formService.byOrgPathPrefix(orgPathPrefix);
    } else {
      rows = formService.mine(userId);
    }
    if (formCode != null && !formCode.isBlank()) {
      rows = rows.stream().filter(r -> formCode.equals(r.formCode())).toList();
    }
    jdbc.update(
        """
        INSERT INTO query_export_audit (user_id, form_code, filter_json, row_count)
        VALUES (?,?,?,?)
        """,
        userId,
        formCode,
        "{\"orgPathPrefix\":\"" + (orgPathPrefix == null ? "" : orgPathPrefix) + "\"}",
        rows.size());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rowCount", rows.size());
    out.put("items", rows);
    return out;
  }
}
