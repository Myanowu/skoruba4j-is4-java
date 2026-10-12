package com.myano.orgplatform.org;

import com.myano.orgplatform.org.OrgModels.CreatePersonRequest;
import com.myano.orgplatform.org.OrgModels.DirectoryResponse;
import com.myano.orgplatform.org.OrgModels.IdpSearchHit;
import com.myano.orgplatform.org.OrgModels.LinkAccountRequest;
import com.myano.orgplatform.org.OrgModels.ProvisionAccountRequest;
import com.myano.orgplatform.org.OrgModels.MembershipDto;
import com.myano.orgplatform.org.OrgModels.OrgRoleDto;
import com.myano.orgplatform.org.OrgModels.OrgUnitDto;
import com.myano.orgplatform.org.OrgModels.PermissionDto;
import com.myano.orgplatform.org.OrgModels.PersonDto;
import com.myano.orgplatform.org.OrgModels.SeedChartResult;
import com.myano.orgplatform.org.OrgModels.UnitWorkspace;
import com.myano.orgplatform.org.OrgModels.UpdatePersonRequest;
import com.myano.orgplatform.org.OrgModels.UpdateUnitRequest;
import com.myano.orgplatform.org.OrgModels.UpsertMembershipRequest;
import com.myano.orgplatform.org.OrgModels.UpsertOrgRoleRequest;
import com.myano.orgplatform.org.OrgModels.UpsertOrgUnitRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/org")
public class OrgController {

  private final OrgService orgService;

  public OrgController(OrgService orgService) {
    this.orgService = orgService;
  }

  @GetMapping("/units/tree")
  public List<OrgUnitDto> tree() {
    return orgService.tree();
  }

  @GetMapping("/units")
  public List<OrgUnitDto> units() {
    return orgService.listUnits();
  }

  @PostMapping("/units")
  public OrgUnitDto createUnit(@RequestBody UpsertOrgUnitRequest body) {
    return orgService.createUnit(body);
  }

  @GetMapping("/units/{id}/roles")
  public List<OrgRoleDto> roles(@PathVariable long id) {
    return orgService.rolesForUnit(id);
  }

  @PostMapping("/roles")
  public OrgRoleDto createRole(@RequestBody UpsertOrgRoleRequest body) {
    return orgService.createRole(body);
  }

  @PostMapping("/roles/{id}/permissions/{code}")
  public Map<String, String> grant(@PathVariable long id, @PathVariable String code) {
    orgService.grantPermission(id, code);
    return Map.of("status", "ok");
  }

  @GetMapping("/roles/{id}/permissions")
  public List<String> rolePermissions(@PathVariable long id) {
    return orgService.rolePermissions(id);
  }

  @GetMapping("/memberships")
  public List<MembershipDto> memberships(@RequestParam String userId) {
    return orgService.membershipsForUser(userId);
  }

  @PostMapping("/memberships")
  public MembershipDto assign(@RequestBody UpsertMembershipRequest body) {
    return orgService.assignMembership(body);
  }

  @GetMapping("/permissions")
  public List<PermissionDto> permissions() {
    return orgService.listPermissions();
  }

  /** Org tree with subtree headcount, plus a flat index for the search box. */
  @GetMapping("/directory")
  public DirectoryResponse directory() {
    return orgService.directory();
  }

  /** Positions and people for the department selected in the tree. */
  @GetMapping("/units/{id}/workspace")
  public UnitWorkspace workspace(@PathVariable long id) {
    return orgService.workspace(id);
  }

  /** Renames a department, changes its type, manager, or enabled flag. */
  @PutMapping("/units/{id}")
  public OrgUnitDto updateUnit(@PathVariable long id, @RequestBody UpdateUnitRequest body) {
    return orgService.updateUnit(id, body);
  }

  /** Inserts the sample company so the tree is usable before real data exists. */
  @PostMapping("/demo/seed-chart")
  public SeedChartResult seedChart() {
    return orgService.seedSampleChart();
  }

  /** Creates a personnel file and optionally assigns them to a department. */
  @PostMapping("/people")
  public PersonDto createPerson(@RequestBody CreatePersonRequest body) {
    return orgService.createPerson(body);
  }

  /** Updates HR fields and the assignment shown in the current department. */
  @PutMapping("/people/{id}")
  public PersonDto updatePerson(@PathVariable long id, @RequestBody UpdatePersonRequest body) {
    return orgService.updatePerson(id, body);
  }

  /** Disables a person and closes their open assignments. */
  @PostMapping("/people/{id}/disable")
  public PersonDto disablePerson(@PathVariable long id) {
    return orgService.disablePerson(id);
  }

  /** Searches STS users so an admin can attach an existing login. */
  @GetMapping("/idp/users")
  public List<IdpSearchHit> searchAccounts(@RequestParam String q) {
    return orgService.searchAccounts(q);
  }

  /** Creates an STS account for this person and stores {@code Users.Id}. */
  @PostMapping("/people/{id}/account")
  public PersonDto provisionAccount(
      @PathVariable long id, @RequestBody ProvisionAccountRequest body) {
    return orgService.provisionAccount(id, body);
  }

  /** Links this person to an existing STS user. */
  @PostMapping("/people/{id}/account/link")
  public PersonDto linkAccount(@PathVariable long id, @RequestBody LinkAccountRequest body) {
    return orgService.linkAccount(id, body);
  }
}
