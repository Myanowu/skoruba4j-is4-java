package com.myano.orgplatform.workflow;

import com.myano.orgplatform.form.FormModels.PublishFormRequest;
import com.myano.orgplatform.form.FormService;
import com.myano.orgplatform.org.OrgModels.UpsertMembershipRequest;
import com.myano.orgplatform.org.OrgModels.UpsertOrgRoleRequest;
import com.myano.orgplatform.org.OrgModels.UpsertOrgUnitRequest;
import com.myano.orgplatform.org.OrgService;
import com.myano.orgplatform.org.OrgUnitRepository;
import com.myano.orgplatform.workflow.WorkflowModels.PublishProcessRequest;
import com.myano.orgplatform.workflow.WorkflowModels.SeedLeaveBundleResult;
import com.myano.orgplatform.workflow.WorkflowModels.UpsertApproverSetRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoSeedService {

  private final OrgService orgService;
  private final OrgUnitRepository units;
  private final FormService formService;
  private final WorkflowService workflowService;

  public DemoSeedService(
      OrgService orgService,
      OrgUnitRepository units,
      FormService formService,
      WorkflowService workflowService) {
    this.orgService = orgService;
    this.units = units;
    this.formService = formService;
    this.workflowService = workflowService;
  }

  /** Seeds the leave demo: a small org, one form, and a two-step approval path. */
  @Transactional
  public SeedLeaveBundleResult seedLeaveBundle() {
    List<String> notes = new ArrayList<>();

    var hq =
        units
            .findByCode("HQ")
            .orElseGet(
                () ->
                    orgService.createUnit(
                        new UpsertOrgUnitRequest(null, "HQ", "總公司", "COMPANY", "manager1")));
    notes.add("org unit HQ id=" + hq.id());

    var finance =
        units
            .findByCode("FIN")
            .orElseGet(
                () ->
                    orgService.createUnit(
                        new UpsertOrgUnitRequest(hq.id(), "FIN", "財務部", "DEPT", "manager1")));

    var managerRole =
        orgService.rolesForUnit(finance.id()).stream()
            .filter(r -> "MANAGER".equals(r.code()))
            .findFirst()
            .orElseGet(
                () ->
                    orgService.createRole(
                        new UpsertOrgRoleRequest(finance.id(), "MANAGER", "財務經理")));
    orgService.grantPermission(managerRole.id(), "TASK_APPROVE");

    if (orgService.membershipsForUser("alice").isEmpty()) {
      orgService.assignMembership(
          new UpsertMembershipRequest(
              "alice", finance.id(), null, "專員", true, null, null));
    }
    if (orgService.membershipsForUser("manager1").isEmpty()) {
      orgService.assignMembership(
          new UpsertMembershipRequest(
              "manager1", finance.id(), managerRole.id(), "財務經理", true, null, null));
    }
    orgService.ensurePerson("alice", "A001", "陳小文", "alice@example.com");
    orgService.ensurePerson("manager1", "A002", "林知遠", "manager@example.com");
    notes.add("users alice (staff) + manager1 (MANAGER role)");

    workflowService.upsertApproverSet(
        new UpsertApproverSetRequest(
            finance.id(),
            "LEAVE_SET",
            "Leave approval set",
            "GENERIC",
            """
            [
              {"order":1,"orgRoleCode":"MANAGER","threshold":null,"resolveStrategy":"ORG_ROLE"},
              {"order":2,"orgRoleCode":"MANAGER","threshold":5.0,"resolveStrategy":"MANAGER_CHAIN"}
            ]
            """));

    workflowService.publishProcess(
        new PublishProcessRequest(
            "leave_approval",
            "Leave approval",
            """
            [
              {
                "key":"approve",
                "name":"Approve",
                "resolveStrategy":"APPROVER_SET",
                "approverSetCode":"LEAVE_SET",
                "orgRoleCode":null,
                "fixedUserId":null,
                "threshold":null
              }
            ]
            """,
            "leave"));

    formService.publish(
        new PublishFormRequest(
            "leave",
            "Leave request",
            """
            {
              "type":"object",
              "properties":{
                "days":{"type":"number","title":"Days"},
                "reason":{"type":"string","title":"Reason"},
                "amount":{"type":"number","title":"Amount (demo threshold)"}
              },
              "required":["days","reason"]
            }
            """,
            "[\"days\",\"reason\",\"amount\"]",
            "leave_approval"));

    notes.add("form leave + process leave_approval + approver set LEAVE_SET");
    return new SeedLeaveBundleResult("leave", "leave_approval", notes);
  }
}
