package com.myano.skoruba4j.adminapi.web;

import com.myano.skoruba4j.adminapi.dto.AdminDtos;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ClientSecretDto;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ClientUpsert;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.ClientsDto;
import com.myano.skoruba4j.adminapi.dto.AdminDtos.SecretCreate;
import com.myano.skoruba4j.domain.PageQuery;
import com.myano.skoruba4j.domain.PageResult;
import com.myano.skoruba4j.domain.configstore.ClientSummary;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.SharedSecretHasher;
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
@RequestMapping("/api/Clients")
public class ClientsController {
  private final Optional<JdbcRepositories> jdbc;

  public ClientsController(Optional<JdbcRepositories> jdbc) {
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
    PageResult<ClientSummary> result = jdbc.get().clients().search(query);
    ClientsDto body =
        new ClientsDto(
            result.pageSize(),
            result.totalCount(),
            result.page(),
            result.items().stream().map(AdminDtos::fromSummary).toList());
    return ResponseEntity.ok(body);
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable int id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    return jdbc.get()
        .clients()
        .findByPk(id)
        .<ResponseEntity<?>>map(c -> ResponseEntity.ok(AdminDtos.fromConfig(c)))
        .orElseGet(ApiResponses::notFound);
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody ClientUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.clientId() == null || body.clientId().isBlank()) {
      return ApiResponses.badRequest("clientId is required");
    }
    int id = jdbc.get().clients().insert(AdminDtos.toWrite(body));
    return ResponseEntity.ok(MapOfId.id(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable int id, @RequestBody ClientUpsert body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (jdbc.get().clients().findByPk(id).isEmpty()) {
      return ApiResponses.notFound();
    }
    jdbc.get().clients().update(id, AdminDtos.toWrite(body));
    return get(id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable int id) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().clients().delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/Secrets")
  public ResponseEntity<?> addSecret(@PathVariable int id, @RequestBody SecretCreate body) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    if (body == null || body.value() == null || body.value().isBlank()) {
      return ApiResponses.badRequest("value is required");
    }
    String hashed = SharedSecretHasher.sha256Base64(body.value());
    int secretId = jdbc.get().clients().addSecret(id, body.type(), hashed);
    return ResponseEntity.ok(new ClientSecretDto(secretId, body.type(), hashed, null));
  }

  @DeleteMapping("/{id}/Secrets/{secretId}")
  public ResponseEntity<?> deleteSecret(@PathVariable int id, @PathVariable int secretId) {
    if (jdbc.isEmpty()) {
      return ApiResponses.noDatabase();
    }
    jdbc.get().clients().deleteSecret(id, secretId);
    return ResponseEntity.noContent().build();
  }

  private record MapOfId(int id) {
    static MapOfId id(int id) {
      return new MapOfId(id);
    }
  }
}
