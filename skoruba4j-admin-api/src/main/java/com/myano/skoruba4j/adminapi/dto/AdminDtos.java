package com.myano.skoruba4j.adminapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.myano.skoruba4j.domain.configstore.ApiResourceSummary;
import com.myano.skoruba4j.domain.configstore.ClientConfiguration;
import com.myano.skoruba4j.domain.configstore.ClientSummary;
import com.myano.skoruba4j.domain.configstore.ClientWrite;
import com.myano.skoruba4j.domain.configstore.PersistedGrantRecord;
import com.myano.skoruba4j.domain.identity.IdentityRole;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {
  private AdminDtos() {}

  public record ClientsDto(int pageSize, int totalCount, int page, List<ClientDto> clients) {}

  public record ClientDto(
      int id,
      String clientId,
      String clientName,
      boolean enabled,
      boolean requireClientSecret,
      boolean requirePkce,
      boolean allowOfflineAccess,
      int accessTokenLifetime,
      int absoluteRefreshTokenLifetime,
      List<String> allowedGrantTypes,
      List<String> allowedScopes,
      List<String> redirectUris,
      List<String> postLogoutRedirectUris,
      List<ClientSecretDto> clientSecrets) {}

  public record ClientSecretDto(int id, String type, String value, Instant expiration) {}

  public record ClientUpsert(
      String clientId,
      String clientName,
      Boolean enabled,
      Boolean requireClientSecret,
      Boolean requirePkce,
      Boolean allowOfflineAccess,
      Integer accessTokenLifetime,
      Integer absoluteRefreshTokenLifetime,
      List<String> allowedGrantTypes,
      List<String> allowedScopes,
      List<String> redirectUris,
      List<String> postLogoutRedirectUris) {}

  public record SecretCreate(String type, String value) {}

  public record UsersDto(int pageSize, int totalCount, int page, List<UserDto> users) {
    public UsersDto {
      users = users == null ? List.of() : users;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record UserDto(
      String id,
      String userName,
      String email,
      boolean emailConfirmed,
      boolean lockoutEnabled,
      boolean twoFactorEnabled,
      String phoneNumber,
      boolean phoneNumberConfirmed,
      int accessFailedCount,
      Instant lockoutEnd) {}

  /** Skoruba IdentityUserDto POST body; extra flags are ignored on create. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UserUpsert(
      String userName,
      String email,
      String password,
      Boolean emailConfirmed,
      Boolean lockoutEnabled,
      String phoneNumber) {}

  public record ChangePasswordRequest(String password, String userId, String confirmPassword) {}

  public record RoleAssignmentRequest(String roleId, String roleName, String userId) {}

  public record UserClaimWrite(String userId, String claimType, String claimValue) {}

  public record RolesDto(int pageSize, int totalCount, int page, List<RoleDto> roles) {
    public RolesDto {
      roles = roles == null ? List.of() : roles;
    }
  }

  public record RoleDto(String id, String name) {}

  public record RoleUpsert(String name) {}

  public record ResourcesDto(int pageSize, int totalCount, int page, List<ResourceDto> items) {}

  public record ResourceDto(int id, String name, String displayName, boolean enabled) {}

  public record ResourceUpsert(String name, String displayName, Boolean enabled) {}

  public record PersistedGrantsDto(
      int pageSize, int totalCount, int page, List<PersistedGrantDto> persistedGrants) {}

  public record PersistedGrantDto(
      String key, String type, String subjectId, String clientId, Instant creationTime, Instant expiration) {}

  public static ClientDto fromSummary(ClientSummary s) {
    return new ClientDto(
        s.id(),
        s.clientId(),
        s.clientName(),
        s.enabled(),
        false,
        false,
        false,
        0,
        0,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public static ClientDto fromConfig(ClientConfiguration c) {
    List<ClientSecretDto> secrets =
        c.secrets().stream()
            .map(sec -> new ClientSecretDto(sec.id(), sec.type(), "", sec.expiration()))
            .toList();
    return new ClientDto(
        c.id(),
        c.clientId(),
        c.clientName(),
        c.enabled(),
        c.requireClientSecret(),
        c.requirePkce(),
        c.allowOfflineAccess(),
        c.accessTokenLifetime(),
        c.absoluteRefreshTokenLifetime(),
        c.grantTypes(),
        c.scopes(),
        c.redirectUris(),
        c.postLogoutRedirectUris(),
        secrets);
  }

  public static ClientWrite toWrite(ClientUpsert body) {
    return ClientWrite.basic(
        body.clientId(),
        body.clientName() == null ? body.clientId() : body.clientName(),
        body.enabled() == null || body.enabled(),
        body.requireClientSecret() == null || body.requireClientSecret(),
        body.requirePkce() != null && body.requirePkce(),
        body.allowOfflineAccess() != null && body.allowOfflineAccess(),
        body.accessTokenLifetime() == null ? 3600 : body.accessTokenLifetime(),
        body.absoluteRefreshTokenLifetime() == null ? 2_592_000 : body.absoluteRefreshTokenLifetime(),
        body.allowedGrantTypes() == null ? List.of() : body.allowedGrantTypes(),
        body.allowedScopes() == null ? List.of() : body.allowedScopes(),
        body.redirectUris() == null ? List.of() : body.redirectUris(),
        body.postLogoutRedirectUris() == null ? List.of() : body.postLogoutRedirectUris());
  }

  public static UserDto fromUser(IdentityUser u) {
    return new UserDto(
        u.id(),
        u.userName() == null ? "" : u.userName(),
        u.email() == null ? "" : u.email(),
        u.emailConfirmed(),
        u.lockoutEnabled(),
        u.twoFactorEnabled(),
        u.phoneNumber() == null ? "" : u.phoneNumber(),
        u.phoneNumberConfirmed(),
        u.accessFailedCount(),
        u.lockoutEnd());
  }

  public static RoleDto fromRole(IdentityRole r) {
    return new RoleDto(r.id(), r.name());
  }

  public static ResourceDto fromResource(ApiResourceSummary s) {
    return new ResourceDto(s.id(), s.name(), s.displayName(), s.enabled());
  }

  public static PersistedGrantDto fromGrant(PersistedGrantRecord g) {
    return new PersistedGrantDto(
        g.key(), g.type(), g.subjectId(), g.clientId(), g.creationTime(), g.expiration());
  }
}
