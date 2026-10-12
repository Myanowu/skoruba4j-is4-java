package com.myano.orgplatform.workflow;

import com.myano.orgplatform.workflow.WorkflowModels.ApproverSetDto;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessDefinitionDto;
import com.myano.orgplatform.workflow.WorkflowModels.ProcessTaskDto;
import com.myano.orgplatform.workflow.WorkflowModels.PublishProcessRequest;
import com.myano.orgplatform.workflow.WorkflowModels.TaskActionRequest;
import com.myano.orgplatform.workflow.WorkflowModels.UpsertApproverSetRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

  private final WorkflowService workflowService;
  private final DemoSeedService demoSeedService;

  public WorkflowController(WorkflowService workflowService, DemoSeedService demoSeedService) {
    this.workflowService = workflowService;
    this.demoSeedService = demoSeedService;
  }

  @PostMapping("/processes/publish")
  public ProcessDefinitionDto publish(@RequestBody PublishProcessRequest body) {
    return workflowService.publishProcess(body);
  }

  @PostMapping("/approver-sets")
  public ApproverSetDto upsertSet(@RequestBody UpsertApproverSetRequest body) {
    return workflowService.upsertApproverSet(body);
  }

  @GetMapping("/approver-sets")
  public List<ApproverSetDto> listSets() {
    return workflowService.listApproverSets();
  }

  @GetMapping("/tasks/inbox")
  public List<ProcessTaskDto> inbox(@RequestParam String userId) {
    return workflowService.inbox(userId);
  }

  @GetMapping("/tasks/done")
  public List<ProcessTaskDto> done(@RequestParam String userId) {
    return workflowService.done(userId);
  }

  @PostMapping("/tasks/{id}/action")
  public ProcessTaskDto act(@PathVariable long id, @RequestBody TaskActionRequest body) {
    return workflowService.act(id, body);
  }

  @PostMapping("/demo/seed-leave")
  public WorkflowModels.SeedLeaveBundleResult seedLeave() {
    return demoSeedService.seedLeaveBundle();
  }
}
