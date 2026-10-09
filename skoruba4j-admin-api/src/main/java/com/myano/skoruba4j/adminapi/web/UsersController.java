package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ChangePasswordRequest;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.RoleAssignmentRequest;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UserClaimDto;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UserClaimWrite;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UserClaimsSyncRequest;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UserUpsert;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UsersDto;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserClaim;
import com.myano.skoruba4j.domain.identity.UserProfileWrite;
import java.net.URI;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/Users")
public class UsersController {
  private final Optional<JdbcRepositories> jdbc;
  private final IdentityPasswordHasher hasher;

  public UsersController(Optional<JdbcRepositories> jdbc, IdentityPasswordHasher hasher) {
    this.jdbc = jdbc;
    this.hasher = hasher;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(required = false) String searchText,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    PageQuery query = PageQuery.of(searchText, page, pageSize);
    PageResult<IdentityUser> result = jdbc.get().users().search(query);
    return ResponseEntity.ok(
        new UsersDto(
            result.pageSize(),
            result.totalCount(),
            result.page(),
            result.items().stream().map(AdminDtos::fromUser).toList()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable String id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    return jdbc.get()
        .users()
        .findById(id)
        .<ResponseEntity<?>>map(u -> ResponseEntity.ok(AdminDtos.fromUser(u)))
        .orElseGet(ApiResponses::notFound);
  }

  @GetMapping("/{id}/Roles")
  public ResponseEntity<?> roles(@PathVariable String id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    return ResponseEntity.ok(
        Map.of(
            "roles",
            jdbc.get().users().listRoles(id).stream().map(AdminDtos::fromRole).toList()));
  }

  /**
   * Bulk create (not SCIM). Body is a JSON array of the same shape as {@code POST /api/Users}.
   * Each row follows the single-create idempotent rule. Cap 200.
   */
  @PostMapping("/Bulk")
  public ResponseEntity<?> bulkCreate(@RequestBody List<UserUpsert> body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.isEmpty()) {
      return ApiResponses.badRequest("body must be a non-empty array");
    }
    if (body.size() > 200) {
      return ApiResponses.badRequest("at most 200 users per request");
    }
    List<Map<String, Object>> results = new ArrayList<>();
    int created = 0;
    int existing = 0;
    int failed = 0;
    for (UserUpsert row : body) {
      Map<String, Object> item = new LinkedHashMap<>();
      if (row == null || row.userName() == null || row.userName().isBlank()) {
        item.put("status", "failed");
        item.put("error", "UserName is required");
        failed++;
        results.add(item);
        continue;
      }
      String userName = row.userName().trim();
      item.put("userName", userName);
      String normalizedUser = userName.toUpperCase(Locale.ROOT);
      try {
        Optional<IdentityUser> before =
            jdbc.get().users().findByNormalizedUserName(normalizedUser);
        ResponseEntity<?> one = create(row);
        if (one.getStatusCode().is2xxSuccessful()
            && one.getBody() instanceof AdminDtos.UserDto dto) {
          item.put("status", "ok");
          item.put("id", dto.id());
          if (before.isPresent()) {
            item.put("result", "existing");
            existing++;
          } else {
            item.put("result", "created");
            created++;
          }
        } else {
          item.put("status", "failed");
          item.put("error", "create rejected");
          failed++;
        }
      } catch (RuntimeException ex) {
        item.put("status", "failed");
        item.put("error", ex.getMessage() == null ? "error" : ex.getMessage());
        failed++;
      }
      results.add(item);
    }
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("created", created);
    summary.put("existing", existing);
    summary.put("failed", failed);
    summary.put("count", results.size());
    summary.put("items", results);
    return ResponseEntity.ok(summary);
  }

  /**
   * Skoruba {@code POST api/Users} returns 201 and the created user. Password is optional: callers
   * such as the 4S API set it with {@code POST api/Users/ChangePassword}.
   *
   * <p>When the user name already exists, return 201 of that user instead of HTTP 400. 4S
   * CreateUser always POSTs then ChangePassword; a prior partial success would otherwise 400 and
   * the NSwag client surfaces only a generic ValidationException (empty ProblemDetails text).
   */
  @PostMapping
  public ResponseEntity<?> create(@RequestBody UserUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.userName() == null || body.userName().isBlank()) {
      return ApiResponses.validation("UserName", "User name is required");
    }
    String userName = body.userName().trim();
    String normalizedUser = userName.toUpperCase(Locale.ROOT);
    Optional<IdentityUser> existing = jdbc.get().users().findByNormalizedUserName(normalizedUser);
    if (existing.isPresent()) {
      IdentityUser user = applyPhone(existing.get(), body.phoneNumber());
      return createdUser(user);
    }
    String rawPassword = body.password();
    if (rawPassword == null || rawPassword.isBlank()) {
      rawPassword = java.util.UUID.randomUUID().toString();
    }
    String id;
    try {
      id =
          jdbc.get()
              .users()
              .insert(
                  userName,
                  body.email(),
                  body.emailConfirmed() != null && body.emailConfirmed(),
                  hasher.hash(rawPassword));
    } catch (UncheckedSqlException ex) {
      Optional<IdentityUser> raced = jdbc.get().users().findByNormalizedUserName(normalizedUser);
      if (raced.isPresent()) {
        return createdUser(applyPhone(raced.get(), body.phoneNumber()));
      }
      return ApiResponses.validation("UserName", "User could not be created");
    }
    IdentityUser created = jdbc.get().users().findById(id).orElseThrow();
    return createdUser(applyPhone(created, body.phoneNumber()));
  }

  private IdentityUser applyPhone(IdentityUser created, String phoneNumber) {
    if (phoneNumber == null || phoneNumber.isBlank()) {
      return created;
    }
    jdbc.get()
        .users()
        .update(
            created.id(),
            new UserProfileWrite(
                created.userName(),
                created.email(),
                created.emailConfirmed(),
                phoneNumber,
                created.phoneNumberConfirmed(),
                created.lockoutEnabled(),
                created.lockoutEnd(),
                created.accessFailedCount(),
                created.twoFactorEnabled()));
    return jdbc.get().users().findById(created.id()).orElse(created);
  }

  private static ResponseEntity<AdminDtos.UserDto> createdUser(IdentityUser user) {
    return ResponseEntity.created(URI.create("/api/Users/" + user.id())).body(AdminDtos.fromUser(user));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @RequestBody UserUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    Optional<IdentityUser> existing = jdbc.get().users().findById(id);
    if (existing.isEmpty()) {
      return ApiResponses.notFound();
    }
    IdentityUser current = existing.get();
    jdbc.get()
        .users()
        .update(
            id,
            new UserProfileWrite(
                body.userName() == null || body.userName().isBlank()
                    ? current.userName()
                    : body.userName(),
                body.email() == null ? current.email() : body.email(),
                body.emailConfirmed() != null
                    ? body.emailConfirmed()
                    : current.emailConfirmed(),
                current.phoneNumber(),
                current.phoneNumberConfirmed(),
                body.lockoutEnabled() != null
                    ? body.lockoutEnabled()
                    : current.lockoutEnabled(),
                current.lockoutEnd(),
                current.accessFailedCount(),
                current.twoFactorEnabled()));
    return get(id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().users().delete(id);
    return ResponseEntity.noContent().build();
  }

  /** Skoruba {@code POST api/Users/ChangePassword} body is userId + password + confirmPassword. */
  @PostMapping("/ChangePassword")
  public ResponseEntity<?> changePasswordByUser(@RequestBody ChangePasswordRequest body) {
    if (body == null || body.userId() == null || body.userId().isBlank()) {
      return ApiResponses.validation("UserId", "userId is required");
    }
    if (body.confirmPassword() != null && !body.confirmPassword().equals(body.password())) {
      return ApiResponses.validation("Password", "password and confirmPassword do not match");
    }
    ResponseEntity<?> result = changePassword(body.userId(), body);
    if (result.getStatusCode().is2xxSuccessful()) {
      return ResponseEntity.ok().build();
    }
    return result;
  }

  @PostMapping("/{id}/ChangePassword")
  public ResponseEntity<?> changePassword(
      @PathVariable String id, @RequestBody ChangePasswordRequest body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.password() == null || body.password().isBlank()) {
      return ApiResponses.badRequest("password is required");
    }
    jdbc.get().users().setPasswordHash(id, hasher.hash(body.password()));
    return ResponseEntity.noContent().build();
  }

  /** Skoruba {@code POST api/Users/Roles} expects HTTP 200 and userId + roleId in the body. */
  @PostMapping("/Roles")
  public ResponseEntity<?> addRoleForUser(@RequestBody RoleAssignmentRequest body) {
    if (body == null || body.userId() == null || body.userId().isBlank()) {
      return ApiResponses.badRequest("userId is required");
    }
    ResponseEntity<?> result = addRole(body.userId(), body);
    if (result.getStatusCode().is2xxSuccessful()) {
      return ResponseEntity.ok().build();
    }
    return result;
  }

  @PostMapping("/{id}/Roles")
  public ResponseEntity<?> addRole(@PathVariable String id, @RequestBody RoleAssignmentRequest body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || ((body.roleId() == null || body.roleId().isBlank()) && (body.roleName() == null || body.roleName().isBlank()))) {
      return ApiResponses.badRequest("roleId or roleName is required");
    }
    String raw = body.roleId() != null && !body.roleId().isBlank() ? body.roleId() : body.roleName();
    var role =
        jdbc.get()
            .roles()
            .findById(raw)
            .or(() -> jdbc.get().roles().findByNormalizedName(raw));
    if (role.isEmpty()) {
      return ApiResponses.badRequest("role not found");
    }
    jdbc.get().users().addRole(id, role.get().id());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/Roles/{roleId}")
  public ResponseEntity<?> removeRole(@PathVariable String id, @PathVariable String roleId) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().users().removeRole(id, roleId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/Claims")
  public ResponseEntity<?> listClaims(@PathVariable String id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (jdbc.get().users().findById(id).isEmpty()) {
      return ApiResponses.notFound();
    }
    List<UserClaimDto> claims =
        jdbc.get().users().listClaims(id).stream()
            .map(c -> new UserClaimDto(c.id(), c.type(), c.value()))
            .toList();
    return ResponseEntity.ok(Map.of("claims", claims));
  }

  /** Skoruba {@code POST api/Users/Claims} assigns one claim (for example position) to userId. */
  @PostMapping("/Claims")
  public ResponseEntity<?> addClaim(@RequestBody UserClaimWrite body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null
        || body.userId() == null
        || body.userId().isBlank()
        || body.claimType() == null
        || body.claimType().isBlank()) {
      return ApiResponses.badRequest("userId and claimType are required");
    }
    if (jdbc.get().users().findById(body.userId()).isEmpty()) {
      return ApiResponses.notFound();
    }
    jdbc.get().users().addClaim(body.userId(), body.claimType(), body.claimValue());
    return ResponseEntity.ok().build();
  }

  /**
   * Org sync: replace only the claim types present in the body. Other UserClaims stay untouched.
   * Example: {@code PUT /api/Users/{id}/Claims/Sync} with {@code department}/{@code org_path}.
   */
  @PutMapping("/{id}/Claims/Sync")
  public ResponseEntity<?> syncClaims(
      @PathVariable String id, @RequestBody UserClaimsSyncRequest body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (jdbc.get().users().findById(id).isEmpty()) {
      return ApiResponses.notFound();
    }
    List<UserClaim> rows = new ArrayList<>();
    if (body != null) {
      for (UserClaimDto dto : body.claims()) {
        if (dto == null || dto.type() == null || dto.type().isBlank()) {
          continue;
        }
        rows.add(new UserClaim(0, dto.type().trim(), dto.value() == null ? "" : dto.value()));
      }
    }
    if (rows.isEmpty()) {
      return ApiResponses.badRequest("claims must contain at least one type");
    }
    jdbc.get().users().replaceClaimsByTypes(id, rows);
    List<UserClaimDto> claims =
        jdbc.get().users().listClaims(id).stream()
            .map(c -> new UserClaimDto(c.id(), c.type(), c.value()))
            .toList();
    return ResponseEntity.ok(Map.of("claims", claims));
  }
}
