package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ResourceUpsert;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ResourcesDto;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ApiResourceAdminRepository;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
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
@RequestMapping("/api/ApiResources")
public class ApiResourcesController {
  private final Optional<JdbcRepositories> jdbc;

  public ApiResourcesController(Optional<JdbcRepositories> jdbc) {
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
    PageResult<ApiResourceSummary> result = repo().search(query);
    return ResponseEntity.ok(
        new ResourcesDto(
            result.pageSize(),
            result.totalCount(),
            result.page(),
            result.items().stream().map(AdminDtos::fromResource).toList()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable int id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    return repo()
        .findByPk(id)
        .<ResponseEntity<?>>map(s -> ResponseEntity.ok(AdminDtos.fromResource(s)))
        .orElseGet(ApiResponses::notFound);
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody ResourceUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.name() == null || body.name().isBlank()) {
      return ApiResponses.badRequest("name is required");
    }
    int id =
        repo()
            .insert(
                body.name(), body.displayName(), body.enabled() == null || body.enabled());
    return ResponseEntity.ok(Map.of("id", id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable int id, @RequestBody ResourceUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    repo()
        .update(
            id,
            body.name(),
            body.displayName(),
            body.enabled() == null || body.enabled());
    return get(id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable int id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    repo().delete(id);
    return ResponseEntity.noContent().build();
  }

  private ApiResourceAdminRepository repo() {
    return jdbc.get().apiResourceAdmin();
  }
}
