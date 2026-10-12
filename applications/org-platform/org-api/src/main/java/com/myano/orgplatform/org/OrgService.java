package com.myano.orgplatform.org;

import com.myano.orgplatform.idp.ClaimsSyncClient;
import com.myano.orgplatform.idp.ClaimsSyncClient.Claim;
import com.myano.orgplatform.idp.IdpDirectory;
import com.myano.orgplatform.idp.IdpDirectory.IdpAccount;
import com.myano.orgplatform.org.OrgModels.CreatePersonRequest;
import com.myano.orgplatform.org.OrgModels.DirectoryNode;
import com.myano.orgplatform.org.OrgModels.DirectoryResponse;
import com.myano.orgplatform.org.OrgModels.IdpSearchHit;
import com.myano.orgplatform.org.OrgModels.LinkAccountRequest;
import com.myano.orgplatform.org.OrgModels.ProvisionAccountRequest;
import com.myano.orgplatform.org.OrgModels.MembershipDto;
import com.myano.orgplatform.org.OrgModels.OrgRoleDto;
import com.myano.orgplatform.org.OrgModels.OrgUnitDto;
import com.myano.orgplatform.org.OrgModels.PersonDto;
import com.myano.orgplatform.org.OrgModels.SeedChartResult;
import com.myano.orgplatform.org.OrgModels.UnitWorkspace;
import com.myano.orgplatform.org.OrgModels.UpdatePersonRequest;
import com.myano.orgplatform.org.OrgModels.UpdateUnitRequest;
import com.myano.orgplatform.org.OrgModels.UpsertMembershipRequest;
import com.myano.orgplatform.org.OrgModels.UpsertOrgRoleRequest;
import com.myano.orgplatform.org.OrgModels.UpsertOrgUnitRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgService {

  private final OrgUnitRepository units;
  private final OrgRoleRepository roles;
  private final MembershipRepository memberships;
  private final PersonRepository people;
  private final ClaimsSyncClient claimsSync;
  private final IdpDirectory idp;
  private final JdbcTemplate jdbc;

  public OrgService(
      OrgUnitRepository units,
      OrgRoleRepository roles,
      MembershipRepository memberships,
      PersonRepository people,
      ClaimsSyncClient claimsSync,
      IdpDirectory idp,
      JdbcTemplate jdbc) {
    this.units = units;
    this.roles = roles;
    this.memberships = memberships;
    this.people = people;
    this.claimsSync = claimsSync;
    this.idp = idp;
    this.jdbc = jdbc;
  }

  public List<OrgUnitDto> tree() {
    return units.asTree();
  }

  public List<OrgUnitDto> listUnits() {
    return units.findAll();
  }

  /** Creates a department and stores its path as the parent path plus the code. */
  @Transactional
  public OrgUnitDto createUnit(UpsertOrgUnitRequest req) {
    if (req.code() == null || req.code().isBlank()) {
      throw new IllegalArgumentException("請填寫部門編碼");
    }
    if (req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("請填寫部門名稱");
    }
    String code = req.code().trim().toUpperCase();
    if (units.findByCode(code).isPresent()) {
      throw new IllegalArgumentException("部門編碼已存在");
    }
    String type = req.unitType() == null || req.unitType().isBlank() ? "DEPT" : req.unitType();
    String parentPath = "";
    if (req.parentId() != null) {
      OrgUnitDto parent =
          units
              .findById(req.parentId())
              .orElseThrow(() -> new IllegalArgumentException("上層部門不存在"));
      parentPath = parent.path();
    }
    String path = parentPath + "/" + code;
    long id =
        units.insert(req.parentId(), code, req.name().trim(), path, type, req.managerUserId());
    return units.findById(id).orElseThrow();
  }

  /** Updates the display fields of a department. Code and parent stay put. */
  @Transactional
  public OrgUnitDto updateUnit(long id, UpdateUnitRequest req) {
    OrgUnitDto current = units.findById(id).orElseThrow(() -> new IllegalArgumentException("部門不存在"));
    if (req.name() != null && req.name().isBlank()) {
      throw new IllegalArgumentException("請填寫部門名稱");
    }
    String name = req.name() == null ? current.name() : req.name().trim();
    String type =
        req.unitType() == null || req.unitType().isBlank() ? current.unitType() : req.unitType();
    String manager = req.managerUserId() == null ? current.managerUserId() : blankToNull(req.managerUserId());
    boolean enabled = req.enabled() == null ? current.enabled() : req.enabled();
    units.update(id, name, type, manager, enabled);
    return units.findById(id).orElseThrow();
  }

  /** Tree plus a flat people index for the org console. */
  public DirectoryResponse directory() {
    return new DirectoryResponse(buildTree(), people.listIndex());
  }

  /** Department detail: headcount, positions, and people currently assigned here. */
  public UnitWorkspace workspace(long unitId) {
    OrgUnitDto unit = units.findById(unitId).orElseThrow(() -> new IllegalArgumentException("部門不存在"));
    DirectoryNode node = findNode(buildTree(), unitId);
    String managerName = null;
    if (unit.managerUserId() != null && !unit.managerUserId().isBlank()) {
      managerName =
          people
              .findByUserKey(unit.managerUserId())
              .map(PersonDto::displayName)
              .orElse(unit.managerUserId());
    }
    int direct = node == null ? 0 : node.directCount();
    int total = node == null ? direct : node.totalCount();
    return new UnitWorkspace(
        unit, managerName, direct, total, roles.findByUnit(unitId), people.listActiveMembers(unitId));
  }

  public List<OrgRoleDto> rolesForUnit(long unitId) {
    return roles.findByUnit(unitId);
  }

  /** Creates a position under a department. Code is unique inside that department. */
  @Transactional
  public OrgRoleDto createRole(UpsertOrgRoleRequest req) {
    if (req.code() == null || req.code().isBlank()) {
      throw new IllegalArgumentException("請填寫崗位編碼");
    }
    if (req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("請填寫崗位名稱");
    }
    units.findById(req.orgUnitId()).orElseThrow(() -> new IllegalArgumentException("部門不存在"));
    String code = req.code().trim().toUpperCase();
    boolean taken = roles.findByUnit(req.orgUnitId()).stream().anyMatch(r -> code.equals(r.code()));
    if (taken) {
      throw new IllegalArgumentException("崗位編碼已存在");
    }
    long id = roles.insert(req.orgUnitId(), code, req.name().trim());
    return roles.findById(id).orElseThrow();
  }

  public void grantPermission(long roleId, String permissionCode) {
    roles.findById(roleId).orElseThrow(() -> new IllegalArgumentException("role not found"));
    roles.grantPermission(roleId, permissionCode);
  }

  public List<String> rolePermissions(long roleId) {
    return roles.permissionsForRole(roleId);
  }

  public List<MembershipDto> membershipsForUser(String userId) {
    return memberships.findByUser(userId);
  }

  @Transactional
  public MembershipDto assignMembership(UpsertMembershipRequest req) {
    if (req.userId() == null || req.userId().isBlank()) {
      throw new IllegalArgumentException("userId required");
    }
    long id =
        memberships.insert(
            req.userId().trim(),
            req.orgUnitId(),
            req.orgRoleId(),
            req.title(),
            req.primary(),
            req.effectiveFrom(),
            req.effectiveTo());
    MembershipDto created =
        memberships.findByUser(req.userId()).stream()
            .filter(m -> m.id() == id)
            .findFirst()
            .orElseThrow();
    if (created.primary()) {
      syncClaims(created);
    }
    return created;
  }

  /**
   * Pushes the primary assignment onto STS. Skips people who are not linked, and always uses
   * {@code Users.Id} rather than the local user key.
   */
  private void syncClaims(MembershipDto m) {
    String idpUserId =
        people.findByUserKey(m.userId()).map(PersonDto::idpUserId).orElse(null);
    if (idpUserId == null || idpUserId.isBlank()) {
      return;
    }
    List<Claim> claims = new ArrayList<>();
    if (m.orgUnitId() != null) {
      units
          .findById(m.orgUnitId())
          .ifPresent(
              u -> {
                claims.add(new Claim("org_id", String.valueOf(u.id())));
                claims.add(new Claim("org_path", u.path()));
                claims.add(new Claim("department", u.name()));
              });
    }
    if (m.orgRoleId() != null) {
      roles.findById(m.orgRoleId()).ifPresent(r -> claims.add(new Claim("org_role", r.code())));
    }
    if (!claims.isEmpty()) {
      claimsSync.syncUserClaims(idpUserId, claims);
    }
  }

  /** Searches STS users and marks those already linked to a personnel file. */
  public List<IdpSearchHit> searchAccounts(String query) {
    requireIdp();
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("請輸入登入名或郵箱");
    }
    return idp.search(query.trim()).stream()
        .map(
            account ->
                new IdpSearchHit(
                    account.id(),
                    account.userName(),
                    account.email(),
                    account.id() != null && people.findByIdpUserId(account.id()).isPresent()))
        .toList();
  }

  /**
   * Creates an STS user and stores {@code Users.Id}. Refuses when the username already exists so
   * an idempotent Admin API 201 cannot silently attach someone else's account.
   */
  @Transactional
  public PersonDto provisionAccount(long personId, ProvisionAccountRequest req) {
    requireIdp();
    PersonDto person = people.findById(personId).orElseThrow(() -> new IllegalArgumentException("人員不存在"));
    if (person.idpUserId() != null && !person.idpUserId().isBlank()) {
      throw new IllegalArgumentException("此人員已開通登入帳號");
    }
    if (req == null || req.userName() == null || req.userName().isBlank()) {
      throw new IllegalArgumentException("請填寫登入名");
    }
    if (req.password() == null || req.password().length() < 8) {
      throw new IllegalArgumentException("初始密碼至少 8 個字元");
    }
    String userName = req.userName().trim();
    String email = blankToNull(req.email());
    boolean taken =
        idp.search(userName).stream().anyMatch(account -> userName.equalsIgnoreCase(account.userName()));
    if (taken) {
      throw new IllegalArgumentException("登入名已存在，請改用關聯已有帳號");
    }
    IdpAccount created = idp.create(userName, email == null ? "" : email, req.password());
    rejectIfLinkedToSomeoneElse(personId, created.id());
    String storedEmail = email == null ? person.email() : email;
    people.linkLogin(personId, created.id(), created.userName() == null ? userName : created.userName(), storedEmail);
    memberships.findPrimary(person.userKey()).ifPresent(this::syncClaims);
    return people.findById(personId).orElseThrow();
  }

  /** Attaches an existing STS user after confirming the id with Admin API. */
  @Transactional
  public PersonDto linkAccount(long personId, LinkAccountRequest req) {
    requireIdp();
    PersonDto person = people.findById(personId).orElseThrow(() -> new IllegalArgumentException("人員不存在"));
    if (req == null || req.idpUserId() == null || req.idpUserId().isBlank()) {
      throw new IllegalArgumentException("請選擇要關聯的帳號");
    }
    String idpUserId = req.idpUserId().trim();
    if (idpUserId.equals(person.idpUserId())) {
      return person;
    }
    if (person.idpUserId() != null && !person.idpUserId().isBlank()) {
      throw new IllegalArgumentException("此人員已開通登入帳號");
    }
    rejectIfLinkedToSomeoneElse(personId, idpUserId);
    IdpAccount account =
        idp.findById(idpUserId).orElseThrow(() -> new IllegalArgumentException("在 STS 找不到這個帳號"));
    String email =
        account.email() == null || account.email().isBlank() ? person.email() : account.email().trim();
    people.linkLogin(personId, account.id(), account.userName(), email);
    memberships.findPrimary(person.userKey()).ifPresent(this::syncClaims);
    return people.findById(personId).orElseThrow();
  }

  /** Blocks two personnel files from sharing one STS user. */
  private void rejectIfLinkedToSomeoneElse(long personId, String idpUserId) {
    people
        .findByIdpUserId(idpUserId)
        .filter(other -> other.id() != personId)
        .ifPresent(other -> {
          throw new IllegalArgumentException("此帳號已關聯其他人員");
        });
  }

  /** Account actions need a configured Admin API. */
  private void requireIdp() {
    if (!idp.enabled()) {
      throw new IllegalStateException("尚未連接 STS。請設定 orgplatform.idp.sync-enabled 與 Admin API 位址。");
    }
  }

  /**
   * Records a personnel file when this user key is new. Existing files are left unchanged so
   * repeated seeds stay idempotent.
   */
  @Transactional
  public PersonDto ensurePerson(String userKey, String employeeNo, String displayName, String email) {
    var existing = people.findByUserKey(userKey);
    if (existing.isPresent()) {
      return existing.get();
    }
    if (people.findByEmployeeNo(employeeNo).isPresent()) {
      throw new IllegalArgumentException("工號已存在");
    }
    long id =
        people.insert(userKey, null, employeeNo, displayName, blankToNull(email), null, "ACTIVE");
    return people.findById(id).orElseThrow();
  }

  /** Creates a personnel file and, when a department is given, an open assignment. */
  @Transactional
  public PersonDto createPerson(CreatePersonRequest req) {
    if (req.displayName() == null || req.displayName().isBlank()) {
      throw new IllegalArgumentException("請填寫姓名");
    }
    if (req.employeeNo() == null || req.employeeNo().isBlank()) {
      throw new IllegalArgumentException("請填寫工號");
    }
    String employeeNo = req.employeeNo().trim();
    String userKey =
        req.userKey() == null || req.userKey().isBlank() ? employeeNo : req.userKey().trim();
    if (people.findByEmployeeNo(employeeNo).isPresent()) {
      throw new IllegalArgumentException("工號已存在");
    }
    if (people.findByUserKey(userKey).isPresent()) {
      throw new IllegalArgumentException("人員識別已存在");
    }
    if (req.orgUnitId() != null) {
      units.findById(req.orgUnitId()).orElseThrow(() -> new IllegalArgumentException("部門不存在"));
    }
    String title = req.title();
    if (req.orgRoleId() != null) {
      OrgRoleDto role =
          roles.findById(req.orgRoleId()).orElseThrow(() -> new IllegalArgumentException("崗位不存在"));
      if (req.orgUnitId() != null && !req.orgUnitId().equals(role.orgUnitId())) {
        throw new IllegalArgumentException("崗位不屬於此部門");
      }
      if (title == null || title.isBlank()) {
        title = role.name();
      }
    }
    long id =
        people.insert(
            userKey,
            null,
            employeeNo,
            req.displayName().trim(),
            blankToNull(req.email()),
            blankToNull(req.phone()),
            "ACTIVE");
    if (req.orgUnitId() != null) {
      boolean primary = req.primary() == null || req.primary();
      assignMembership(
          new UpsertMembershipRequest(
              userKey, req.orgUnitId(), req.orgRoleId(), title, primary, null, null));
    }
    return people.findById(id).orElseThrow();
  }

  /** Updates the HR profile and, when a membership id is present, that assignment. */
  @Transactional
  public PersonDto updatePerson(long id, UpdatePersonRequest req) {
    PersonDto current = people.findById(id).orElseThrow(() -> new IllegalArgumentException("人員不存在"));
    if (req.displayName() != null && req.displayName().isBlank()) {
      throw new IllegalArgumentException("請填寫姓名");
    }
    if (req.employeeNo() != null && req.employeeNo().isBlank()) {
      throw new IllegalArgumentException("請填寫工號");
    }
    String employeeNo = req.employeeNo() == null ? current.employeeNo() : req.employeeNo().trim();
    people
        .findByEmployeeNo(employeeNo)
        .filter(other -> other.id() != id)
        .ifPresent(other -> {
          throw new IllegalArgumentException("工號已存在");
        });
    String name = req.displayName() == null ? current.displayName() : req.displayName().trim();
    String email = req.email() == null ? current.email() : blankToNull(req.email());
    String phone = req.phone() == null ? current.phone() : blankToNull(req.phone());
    people.update(id, employeeNo, name, email, phone, current.status());
    if (req.membershipId() != null) {
      MembershipDto membership =
          memberships
              .findById(req.membershipId())
              .orElseThrow(() -> new IllegalArgumentException("任職不存在"));
      if (!current.userKey().equals(membership.userId())) {
        throw new IllegalArgumentException("任職不屬於此人員");
      }
      String title = req.title();
      Long roleId = req.orgRoleId();
      if (roleId != null) {
        OrgRoleDto role =
            roles.findById(roleId).orElseThrow(() -> new IllegalArgumentException("崗位不存在"));
        if (membership.orgUnitId() != null && !membership.orgUnitId().equals(role.orgUnitId())) {
          throw new IllegalArgumentException("崗位不屬於此部門");
        }
        if (title == null || title.isBlank()) {
          title = role.name();
        }
      }
      boolean primary = req.primary() == null ? membership.primary() : req.primary();
      memberships.updateAssignment(membership.id(), current.userKey(), roleId, title, primary);
      if (primary) {
        memberships
            .findById(membership.id())
            .ifPresent(this::syncClaims);
      }
    }
    return people.findById(id).orElseThrow();
  }

  /** Marks the person disabled and closes their open assignments. */
  @Transactional
  public PersonDto disablePerson(long id) {
    PersonDto current = people.findById(id).orElseThrow(() -> new IllegalArgumentException("人員不存在"));
    people.update(
        id,
        current.employeeNo(),
        current.displayName(),
        current.email(),
        current.phone(),
        "DISABLED");
    memberships.closeActiveForUser(current.userKey());
    return people.findById(id).orElseThrow();
  }

  /**
   * Fills a small traditional-Chinese org chart. Known department codes are renamed in place so
   * running this twice does not duplicate nodes.
   */
  @Transactional
  public SeedChartResult seedSampleChart() {
    OrgUnitDto hq = ensureUnit(null, "HQ", "總公司", "COMPANY");
    OrgUnitDto fin = ensureUnit(hq.id(), "FIN", "財務部", "DEPT");
    OrgUnitDto acc = ensureUnit(fin.id(), "ACC", "會計組", "TEAM");
    OrgUnitDto rd = ensureUnit(hq.id(), "RD", "研發部", "DEPT");
    OrgUnitDto hr = ensureUnit(hq.id(), "HR", "人力資源部", "DEPT");

    OrgRoleDto gm = ensureRole(hq.id(), "GM", "總經理");
    OrgRoleDto manager = ensureRole(fin.id(), "MANAGER", "財務經理");
    OrgRoleDto staff = ensureRole(fin.id(), "STAFF", "專員");
    OrgRoleDto accountant = ensureRole(acc.id(), "ACCOUNTANT", "會計");
    OrgRoleDto lead = ensureRole(rd.id(), "LEAD", "研發主管");
    OrgRoleDto engineer = ensureRole(rd.id(), "ENGINEER", "工程師");
    OrgRoleDto hrbp = ensureRole(hr.id(), "HRBP", "人事專員");

    place("E001", "E001", "周啟明", "zhou@example.com", hq.id(), gm.id(), "總經理");
    place("manager1", "A002", "林知遠", "manager@example.com", fin.id(), manager.id(), "財務經理");
    place("alice", "A001", "陳小文", "alice@example.com", fin.id(), staff.id(), "專員");
    place("E002", "E002", "王敏", "wang@example.com", acc.id(), accountant.id(), "會計");
    place("E003", "E003", "張承恩", "zhang@example.com", rd.id(), lead.id(), "研發主管");
    place("E004", "E004", "何品妍", "he@example.com", rd.id(), engineer.id(), "工程師");
    place("E005", "E005", "黃子琪", "huang@example.com", hr.id(), hrbp.id(), "人事專員");

    setManager(hq.id(), "E001");
    setManager(fin.id(), "manager1");
    setManager(acc.id(), "E002");
    setManager(rd.id(), "E003");
    setManager(hr.id(), "E005");
    return new SeedChartResult(units.findAll().size(), people.count());
  }

  /** Creates the department or refreshes its name and type when the code already exists. */
  private OrgUnitDto ensureUnit(Long parentId, String code, String name, String type) {
    var existing = units.findByCode(code);
    if (existing.isPresent()) {
      OrgUnitDto current = existing.get();
      units.update(current.id(), name, type, current.managerUserId(), current.enabled());
      return units.findById(current.id()).orElseThrow();
    }
    return createUnit(new UpsertOrgUnitRequest(parentId, code, name, type, null));
  }

  /** Creates the position or refreshes its display name when the code already exists. */
  private OrgRoleDto ensureRole(long unitId, String code, String name) {
    var found =
        roles.findByUnit(unitId).stream().filter(r -> code.equals(r.code())).findFirst();
    if (found.isPresent()) {
      if (!name.equals(found.get().name())) {
        roles.updateName(found.get().id(), name);
        return roles.findById(found.get().id()).orElseThrow();
      }
      return found.get();
    }
    return createRole(new UpsertOrgRoleRequest(unitId, code, name));
  }

  /** Ensures the person exists and has an open assignment in the department. */
  private void place(
      String userKey,
      String employeeNo,
      String displayName,
      String email,
      long unitId,
      long roleId,
      String title) {
    ensurePerson(userKey, employeeNo, displayName, email);
    var open =
        memberships.findByUser(userKey).stream()
            .filter(
                m ->
                    m.orgUnitId() != null
                        && m.orgUnitId() == unitId
                        && m.effectiveTo() == null)
            .findFirst();
    if (open.isEmpty()) {
      assignMembership(
          new UpsertMembershipRequest(userKey, unitId, roleId, title, true, null, null));
      return;
    }
    if (open.get().orgRoleId() == null) {
      memberships.updateAssignment(open.get().id(), userKey, roleId, title, true);
    }
  }

  /** Sets the department manager without renaming the node. */
  private void setManager(long unitId, String userKey) {
    OrgUnitDto unit = units.findById(unitId).orElseThrow();
    units.update(unitId, unit.name(), unit.unitType(), userKey, unit.enabled());
  }

  /** Builds the org tree with subtree headcount rolled up from open assignments. */
  private List<DirectoryNode> buildTree() {
    List<OrgUnitDto> flat = units.findAll();
    Map<Long, Integer> direct = memberships.countActiveByUnit();
    Map<Long, NodeBuilder> byId = new LinkedHashMap<>();
    for (OrgUnitDto unit : flat) {
      byId.put(unit.id(), new NodeBuilder(unit, direct.getOrDefault(unit.id(), 0)));
    }
    List<NodeBuilder> roots = new ArrayList<>();
    for (NodeBuilder node : byId.values()) {
      Long parentId = node.unit.parentId();
      if (parentId == null || !byId.containsKey(parentId)) {
        roots.add(node);
      } else {
        byId.get(parentId).children.add(node);
      }
    }
    roots.sort(Comparator.comparing(n -> n.unit.code()));
    return roots.stream().map(NodeBuilder::freeze).toList();
  }

  /** Depth-first lookup used to attach headcount onto a workspace. */
  private DirectoryNode findNode(List<DirectoryNode> nodes, long id) {
    for (DirectoryNode node : nodes) {
      if (node.id() == id) {
        return node;
      }
      DirectoryNode child = findNode(node.children(), id);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  /** Trims blank strings to null so optional profile columns stay empty. */
  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  /** Mutable tree node used while subtree counts are rolled up. */
  private static final class NodeBuilder {
    private final OrgUnitDto unit;
    private final int direct;
    private final List<NodeBuilder> children = new ArrayList<>();

    private NodeBuilder(OrgUnitDto unit, int direct) {
      this.unit = unit;
      this.direct = direct;
    }

    /** Freezes this node after children have contributed their headcount. */
    private DirectoryNode freeze() {
      children.sort(Comparator.comparing(n -> n.unit.code()));
      List<DirectoryNode> kids = new ArrayList<>();
      int total = direct;
      for (NodeBuilder child : children) {
        DirectoryNode frozen = child.freeze();
        total += frozen.totalCount();
        kids.add(frozen);
      }
      return new DirectoryNode(
          unit.id(),
          unit.parentId(),
          unit.code(),
          unit.name(),
          unit.path(),
          unit.unitType(),
          unit.enabled(),
          direct,
          total,
          kids);
    }
  }

  public List<OrgModels.PermissionDto> listPermissions() {
    return jdbc.query(
        "SELECT code, name, category FROM permission_code ORDER BY category, code",
        (rs, i) ->
            new OrgModels.PermissionDto(
                rs.getString("code"), rs.getString("name"), rs.getString("category")));
  }
}
