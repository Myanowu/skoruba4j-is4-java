package com.myano.skoruba4j.adminapi.web.ui;

import com.myano.skoruba4j.adminapi.web.ui.ApiUiHtml.DebugEndpoint;
import java.util.List;
import java.util.Optional;

/** Preset /api calls for each console section. */
public final class ApiDebugPresets {
  private ApiDebugPresets() {}

  public record ApiSection(String key, String label, String href, List<DebugEndpoint> endpoints) {}

  public static List<ApiSection> sections() {
    return List.of(
        new ApiSection("clients", "Clients", "/ui/api?resource=clients", clients()),
        new ApiSection("users", "Users", "/ui/api?resource=users", users()),
        new ApiSection("roles", "Roles", "/ui/api?resource=roles", roles()),
        new ApiSection(
            "api-resources",
            "ApiResources",
            "/ui/api?resource=api-resources",
            named("/api/ApiResources")),
        new ApiSection(
            "api-scopes", "ApiScopes", "/ui/api?resource=api-scopes", named("/api/ApiScopes")),
        new ApiSection(
            "identity-resources",
            "IdentityResources",
            "/ui/api?resource=identity-resources",
            named("/api/IdentityResources")),
        new ApiSection("grants", "PersistedGrants", "/ui/api?resource=grants", grants()),
        new ApiSection("health", "Health", "/ui/api?resource=health", healthOnly()));
  }

  /** Browser data-list page for a section (not the API explorer). */
  public static String dataHref(String key) {
    return switch (key == null ? "" : key.toLowerCase()) {
      case "clients" -> "/ui/clients";
      case "users" -> "/ui/users";
      case "roles" -> "/ui/roles";
      case "api-resources" -> "/ui/api-resources";
      case "api-scopes" -> "/ui/api-scopes";
      case "identity-resources" -> "/ui/identity-resources";
      case "grants" -> "/ui/grants";
      default -> null;
    };
  }

  public static Optional<ApiSection> find(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return sections().stream().filter(s -> s.key().equalsIgnoreCase(key)).findFirst();
  }

  public static List<DebugEndpoint> endpointsFor(String key) {
    return find(key).map(ApiSection::endpoints).orElseGet(ApiDebugPresets::home);
  }

  public static List<DebugEndpoint> home() {
    return List.of(
        new DebugEndpoint("List clients", "GET", "/api/Clients?page=1&pageSize=10"),
        new DebugEndpoint("List users", "GET", "/api/Users?page=1&pageSize=10"),
        new DebugEndpoint("List roles", "GET", "/api/Roles?page=1&pageSize=10"),
        new DebugEndpoint("List api resources", "GET", "/api/ApiResources?page=1&pageSize=10"),
        new DebugEndpoint("List api scopes", "GET", "/api/ApiScopes?page=1&pageSize=10"),
        new DebugEndpoint(
            "List identity resources", "GET", "/api/IdentityResources?page=1&pageSize=10"),
        new DebugEndpoint("List grants", "GET", "/api/PersistedGrants?page=1&pageSize=10"),
        new DebugEndpoint("Health", "GET", "/health"));
  }

  public static List<DebugEndpoint> healthOnly() {
    return List.of(new DebugEndpoint("Health", "GET", "/health"));
  }

  public static List<DebugEndpoint> clients() {
    return List.of(
        new DebugEndpoint("List", "GET", "/api/Clients?page=1&pageSize=20"),
        new DebugEndpoint("Get by id", "GET", "/api/Clients/1"),
        new DebugEndpoint(
            "Create",
            "POST",
            "/api/Clients",
            "{\n  \"clientId\": \"demo-new\",\n  \"clientName\": \"Demo\",\n  \"enabled\": true,\n  \"requireClientSecret\": false,\n  \"requirePkce\": true,\n  \"allowedGrantTypes\": [\"authorization_code\"],\n  \"allowedScopes\": [\"openid\", \"profile\"],\n  \"redirectUris\": [\"https://localhost:6061/signin-oidc\"]\n}"),
        new DebugEndpoint(
            "Update",
            "PUT",
            "/api/Clients/1",
            "{\n  \"clientId\": \"skoruba4j-admin\",\n  \"clientName\": \"Admin\",\n  \"enabled\": true\n}"),
        new DebugEndpoint("Delete", "DELETE", "/api/Clients/0"),
        new DebugEndpoint(
            "Add secret",
            "POST",
            "/api/Clients/1/Secrets",
            "{\n  \"value\": \"change-me\",\n  \"description\": \"debug\"\n}"),
        new DebugEndpoint("Delete secret", "DELETE", "/api/Clients/1/Secrets/1"));
  }

  public static List<DebugEndpoint> users() {
    return List.of(
        new DebugEndpoint("List", "GET", "/api/Users?page=1&pageSize=20"),
        new DebugEndpoint("Get by id", "GET", "/api/Users/{id}"),
        new DebugEndpoint("Roles of user", "GET", "/api/Users/{id}/Roles"),
        new DebugEndpoint(
            "Create",
            "POST",
            "/api/Users",
            "{\n  \"userName\": \"newuser\",\n  \"email\": \"newuser@example.com\",\n  \"password\": \"Passw0rd!\",\n  \"emailConfirmed\": true\n}"),
        new DebugEndpoint(
            "Update",
            "PUT",
            "/api/Users/{id}",
            "{\n  \"userName\": \"demo\",\n  \"email\": \"demo@example.com\",\n  \"emailConfirmed\": true,\n  \"lockoutEnabled\": false\n}"),
        new DebugEndpoint("Delete", "DELETE", "/api/Users/{id}"),
        new DebugEndpoint(
            "Change password",
            "POST",
            "/api/Users/{id}/ChangePassword",
            "{\n  \"password\": \"Passw0rd!\"\n}"),
        new DebugEndpoint(
            "Assign role",
            "POST",
            "/api/Users/{id}/Roles",
            "{\n  \"roleName\": \"MyRole\"\n}"),
        new DebugEndpoint("Remove role", "DELETE", "/api/Users/{id}/Roles/{roleId}"));
  }

  public static List<DebugEndpoint> roles() {
    return List.of(
        new DebugEndpoint("List", "GET", "/api/Roles?page=1&pageSize=20"),
        new DebugEndpoint("Get by id", "GET", "/api/Roles/{id}"),
        new DebugEndpoint("Create", "POST", "/api/Roles", "{\n  \"name\": \"NewRole\"\n}"),
        new DebugEndpoint("Update", "PUT", "/api/Roles/{id}", "{\n  \"name\": \"RenamedRole\"\n}"),
        new DebugEndpoint("Delete", "DELETE", "/api/Roles/{id}"));
  }

  public static List<DebugEndpoint> named(String apiBase) {
    return List.of(
        new DebugEndpoint("List", "GET", apiBase + "?page=1&pageSize=20"),
        new DebugEndpoint("Get by id", "GET", apiBase + "/1"),
        new DebugEndpoint(
            "Create",
            "POST",
            apiBase,
            "{\n  \"name\": \"new-resource\",\n  \"displayName\": \"New\",\n  \"enabled\": true\n}"),
        new DebugEndpoint(
            "Update",
            "PUT",
            apiBase + "/1",
            "{\n  \"name\": \"updated\",\n  \"displayName\": \"Updated\",\n  \"enabled\": true\n}"),
        new DebugEndpoint("Delete", "DELETE", apiBase + "/0"));
  }

  public static List<DebugEndpoint> grants() {
    return List.of(
        new DebugEndpoint("List", "GET", "/api/PersistedGrants?page=1&pageSize=20"),
        new DebugEndpoint("Delete by key", "DELETE", "/api/PersistedGrants/{key}"),
        new DebugEndpoint(
            "Delete by subject", "DELETE", "/api/PersistedGrants/Subjects/{subjectId}"));
  }
}
