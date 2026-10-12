package com.myano.orgplatform.org;

import java.util.List;

public final class OrgModels {
  private OrgModels() {}

  public record OrgUnitDto(
      Long id,
      Long parentId,
      String code,
      String name,
      String path,
      String unitType,
      boolean enabled,
      String managerUserId,
      List<OrgUnitDto> children) {}

  public record UpsertOrgUnitRequest(
      Long parentId, String code, String name, String unitType, String managerUserId) {}

  public record OrgRoleDto(Long id, Long orgUnitId, String code, String name) {}

  public record UpsertOrgRoleRequest(Long orgUnitId, String code, String name) {}

  public record MembershipDto(
      Long id,
      String userId,
      Long orgUnitId,
      Long orgRoleId,
      String title,
      boolean primary,
      String effectiveFrom,
      String effectiveTo) {}

  public record UpsertMembershipRequest(
      String userId,
      Long orgUnitId,
      Long orgRoleId,
      String title,
      boolean primary,
      String effectiveFrom,
      String effectiveTo) {}

  public record PermissionDto(String code, String name, String category) {}

  public record UpdateUnitRequest(String name, String unitType, String managerUserId, Boolean enabled) {}

  /** One node in the org tree, with direct and subtree headcount. */
  public record DirectoryNode(
      long id,
      Long parentId,
      String code,
      String name,
      String path,
      String unitType,
      boolean enabled,
      int directCount,
      int totalCount,
      List<DirectoryNode> children) {}

  /** Search row: a person and the department of their primary assignment. */
  public record PersonIndex(
      long personId,
      String displayName,
      String employeeNo,
      String userKey,
      String email,
      Long orgUnitId,
      String unitName) {}

  public record DirectoryResponse(List<DirectoryNode> tree, List<PersonIndex> people) {}

  public record PersonDto(
      long id,
      String userKey,
      String idpUserId,
      String loginName,
      String employeeNo,
      String displayName,
      String email,
      String phone,
      String status) {}

  /** Opens a new STS account and stores its {@code Users.Id} on the person. */
  public record ProvisionAccountRequest(String userName, String email, String password) {}

  /** Links a person to an existing STS user. {@code idpUserId} is {@code Users.Id}. */
  public record LinkAccountRequest(String idpUserId) {}

  /** One STS user shown while attaching an existing login. */
  public record IdpSearchHit(String id, String userName, String email, boolean linked) {}

  public record CreatePersonRequest(
      String displayName,
      String employeeNo,
      String userKey,
      String email,
      String phone,
      Long orgUnitId,
      Long orgRoleId,
      String title,
      Boolean primary) {}

  public record UpdatePersonRequest(
      String displayName,
      String employeeNo,
      String email,
      String phone,
      Long membershipId,
      Long orgRoleId,
      String title,
      Boolean primary) {}

  /** Person plus their open assignment in one department. */
  public record MemberRow(
      long membershipId,
      Long personId,
      String userKey,
      String displayName,
      String employeeNo,
      String email,
      String phone,
      String status,
      Long orgRoleId,
      String roleName,
      String title,
      boolean primary,
      String loginName,
      boolean accountLinked) {}

  public record UnitWorkspace(
      OrgUnitDto unit,
      String managerName,
      int directCount,
      int totalCount,
      List<OrgRoleDto> roles,
      List<MemberRow> members) {}

  public record SeedChartResult(int units, int people) {}
}
