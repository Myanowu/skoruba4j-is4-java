package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.PersistedGrantsDto;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRecord;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/PersistedGrants")
public class PersistedGrantsController {
  private final Optional<JdbcRepositories> jdbc;

  public PersistedGrantsController(Optional<JdbcRepositories> jdbc) {
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
    PageResult<PersistedGrantRecord> result = jdbc.get().persistedGrants().search(query);
    return ResponseEntity.ok(
        new PersistedGrantsDto(
            result.pageSize(),
            result.totalCount(),
            result.page(),
            result.items().stream().map(AdminDtos::fromGrant).toList()));
  }

  @DeleteMapping("/{key}")
  public ResponseEntity<?> delete(@PathVariable String key) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().persistedGrants().deleteByKey(key);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/Subjects/{subjectId}")
  public ResponseEntity<?> deleteBySubject(@PathVariable String subjectId) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().persistedGrants().deleteBySubject(subjectId);
    return ResponseEntity.noContent().build();
  }
}
