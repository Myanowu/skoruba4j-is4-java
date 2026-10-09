package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.RoleUpsert;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.RolesDto;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.identity.IdentityRole;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
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
@RequestMapping("/api/Roles")
public class RolesController {
  private final Optional<JdbcRepositories> jdbc;

  public RolesController(Optional<JdbcRepositories> jdbc) {
    this.jdbc = jdbc;
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
    PageResult<IdentityRole> result = jdbc.get().roles().search(query);
    return ResponseEntity.ok(
        new RolesDto(
            result.pageSize(),
            result.totalCount(),
            result.page(),
            result.items().stream().map(AdminDtos::fromRole).toList()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable String id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    return jdbc.get()
        .roles()
        .findById(id)
        .<ResponseEntity<?>>map(r -> ResponseEntity.ok(AdminDtos.fromRole(r)))
        .orElseGet(ApiResponses::notFound);
  }

  /** Skoruba {@code POST api/Roles} returns 201 and {@code id} plus {@code name}. */
  @PostMapping
  public ResponseEntity<?> create(@RequestBody RoleUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.name() == null || body.name().isBlank()) {
      return ApiResponses.badRequest("name is required");
    }
    var existing = jdbc.get().roles().findByNormalizedName(body.name());
    if (existing.isPresent()) {
      IdentityRole role = existing.get();
      return ResponseEntity.created(java.net.URI.create("/api/Roles/" + role.id()))
          .body(AdminDtos.fromRole(role));
    }
    String id = jdbc.get().roles().insert(body.name());
    return ResponseEntity.created(java.net.URI.create("/api/Roles/" + id))
        .body(new AdminDtos.RoleDto(id, body.name()));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @RequestBody RoleUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().roles().update(id, body.name());
    return get(id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().roles().delete(id);
    return ResponseEntity.noContent().build();
  }
}
