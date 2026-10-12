package com.myano.orgplatform.idp;

import com.myano.orgplatform.config.OrgPlatformProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClaimsSyncClient {

  private static final Logger log = LoggerFactory.getLogger(ClaimsSyncClient.class);

  private final OrgPlatformProperties properties;
  private final RestClient.Builder restClientBuilder;

  public ClaimsSyncClient(OrgPlatformProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  public void syncUserClaims(String userId, List<Claim> claims) {
    if (!properties.getIdp().isSyncEnabled()) {
      log.debug("Claims sync skipped (orgplatform.idp.sync-enabled=false) for user {}", userId);
      return;
    }
    String base = properties.getIdp().getAdminApiBaseUrl();
    String token = properties.getIdp().getBearerToken();
    if (base == null || base.isBlank()) {
      log.warn("Claims sync enabled but admin-api-base-url is empty");
      return;
    }
    try {
      RestClient client = restClientBuilder.baseUrl(base).build();
      var req =
          client
              .put()
              .uri("/api/Users/{id}/Claims/Sync", userId)
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("claims", claims.stream().map(c -> Map.of("type", c.type(), "value", c.value())).toList()));
      if (token != null && !token.isBlank()) {
        req = req.header("Authorization", "Bearer " + token);
      }
      req.retrieve().toBodilessEntity();
      log.info("Synced {} claims for user {}", claims.size(), userId);
    } catch (Exception ex) {
      log.warn("Claims sync failed for {}: {}", userId, ex.getMessage());
    }
  }

  public record Claim(String type, String value) {}
}
