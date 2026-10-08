package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ChangePasswordRequest;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.RoleAssignmentRequest;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UserUpsert;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.UsersDto;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.domain.identity.UserProfileWrite;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
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
    return ResponseEntity.ok(Map.of("roles", jdbc.get().users().listRoleNames(id)));
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody UserUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.userName() == null || body.userName().isBlank()) {
      return ApiResponses.badRequest("userName is required");
    }
    if (body.password() == null || body.password().isBlank()) {
      return ApiResponses.badRequest("password is required");
    }
    String hash = hasher.hash(body.password());
    String id =
        jdbc.get()
            .users()
            .insert(
                body.userName(),
                body.email(),
                body.emailConfirmed() != null && body.emailConfirmed(),
                hash);
    return ResponseEntity.ok(Map.of("id", id));
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
}
