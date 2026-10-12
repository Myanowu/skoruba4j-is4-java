package com.myano.orgplatform.workflow;

import java.util.List;

public final class WorkflowModels {
  private WorkflowModels() {}

  public record ProcessDefinitionDto(
      Long id, String code, int version, String title, String nodesJson, String boundFormCode, String status) {}

  public record PublishProcessRequest(String code, String title, String nodesJson, String boundFormCode) {}

  /** Node in nodes_json array. */
  public record ProcessNode(
      String key,
      String name,
      String resolveStrategy,
      String orgRoleCode,
      String fixedUserId,
      String approverSetCode,
      Double threshold) {}

  public record ProcessInstanceDto(
      Long id,
      String processCode,
      Long formInstanceId,
      String status,
      String currentNodeKey,
      String starterUserId,
      Long orgUnitId,
      Double amount) {}

  public record ProcessTaskDto(
      Long id,
      Long processInstanceId,
      String nodeKey,
      String candidateUserIds,
      String candidateGroup,
      String status,
      String action,
      String actionedBy,
      String remarks,
      String createdAt) {}

  public record TaskActionRequest(String userId, String action, String remarks) {}

  public record ApproverSetDto(Long id, Long orgUnitId, String code, String title, String kind, String stepsJson) {}

  public record UpsertApproverSetRequest(
      Long orgUnitId, String code, String title, String kind, String stepsJson) {}

  public record ApproverStep(int order, String orgRoleCode, Double threshold, String resolveStrategy) {}

  public record SeedLeaveBundleResult(String formCode, String processCode, List<String> notes) {}
}
