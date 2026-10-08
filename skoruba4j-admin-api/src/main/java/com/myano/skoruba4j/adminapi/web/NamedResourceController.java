package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ResourceUpsert;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ResourcesDto;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.configstore.NamedResourceRepository;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

abstract class NamedResourceController {
  private final Optional<JdbcRepositories> jdbc;
  private final Function<JdbcRepositories, NamedResourceRepository> repo;

  NamedResourceController(
      Optional<JdbcRepositories> jdbc, Function<JdbcRepositories, NamedResourceRepository> repo) {
    this.jdbc = jdbc;
    this.repo = repo;
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
    PageResult<ApiResourceSummary> result = repo.apply(jdbc.get()).search(query);
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
    return repo.apply(jdbc.get())
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
        repo.apply(jdbc.get())
            .insert(
                body.name(),
                body.displayName(),
                body.enabled() == null || body.enabled());
    return ResponseEntity.ok(Map.of("id", id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable int id, @RequestBody ResourceUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    repo.apply(jdbc.get())
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
    repo.apply(jdbc.get()).delete(id);
    return ResponseEntity.noContent().build();
  }
}
