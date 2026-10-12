package com.myano.orgplatform.idp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myano.orgplatform.config.OrgPlatformProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Admin API client for {@code /api/Users}. Does not store passwords. */
@Component
public class HttpIdpDirectory implements IdpDirectory {

  private final OrgPlatformProperties properties;
  private final RestClient.Builder restClientBuilder;

  public HttpIdpDirectory(OrgPlatformProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  /** True when claim sync is turned on and an Admin API base URL is set. */
  @Override
  public boolean enabled() {
    String base = properties.getIdp().getAdminApiBaseUrl();
    return properties.getIdp().isSyncEnabled() && base != null && !base.isBlank();
  }

  /** {@code GET /api/Users?searchText=} matches username and email. */
  @Override
  public List<IdpAccount> search(String text) {
    try {
      UsersPage page =
          client()
              .get()
              .uri(
                  uri ->
                      uri.path("/api/Users")
                          .queryParam("searchText", text)
                          .queryParam("page", 1)
                          .queryParam("pageSize", 8)
                          .build())
              .retrieve()
              .body(UsersPage.class);
      if (page == null || page.users() == null) {
        return List.of();
      }
      return page.users();
    } catch (RestClientException ex) {
      throw failure(ex);
    }
  }

  /** {@code GET /api/Users/{id}}. A 404 is an empty result, not a transport failure. */
  @Override
  public Optional<IdpAccount> findById(String id) {
    try {
      return Optional.ofNullable(
          client().get().uri("/api/Users/{id}", id).retrieve().body(IdpAccount.class));
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw failure(ex);
    } catch (RestClientException ex) {
      throw failure(ex);
    }
  }

  /** {@code POST /api/Users}. The returned {@code id} is {@code Users.Id}. */
  @Override
  public IdpAccount create(String userName, String email, String password) {
    try {
      IdpAccount created =
          client()
              .post()
              .uri("/api/Users")
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Map.of(
                      "userName",
                      userName,
                      "email",
                      email == null ? "" : email,
                      "password",
                      password,
                      "emailConfirmed",
                      email != null && !email.isBlank()))
              .retrieve()
              .body(IdpAccount.class);
      if (created == null || created.id() == null || created.id().isBlank()) {
        throw new IllegalStateException("STS 沒有返回帳號 Id");
      }
      return created;
    } catch (RestClientException ex) {
      throw failure(ex);
    }
  }

  /** Builds a client for one call. The bearer token is not kept on the shared builder. */
  private RestClient client() {
    RestClient client = restClientBuilder.baseUrl(properties.getIdp().getAdminApiBaseUrl()).build();
    String token = properties.getIdp().getBearerToken();
    if (token == null || token.isBlank()) {
      return client;
    }
    return client.mutate().defaultHeader("Authorization", "Bearer " + token).build();
  }

  /** Maps transport failures to a message the org UI can show. */
  private static IllegalStateException failure(RestClientException ex) {
    if (ex instanceof RestClientResponseException response) {
      return new IllegalStateException("STS 拒絕了這個帳號操作（" + response.getStatusCode().value() + "）");
    }
    return new IllegalStateException("無法連接 STS Admin API");
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record UsersPage(List<IdpAccount> users) {}
}
