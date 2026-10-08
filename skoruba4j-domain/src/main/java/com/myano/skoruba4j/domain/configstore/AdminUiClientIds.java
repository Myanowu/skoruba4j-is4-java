package com.myano.skoruba4j.domain.configstore;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import java.util.Optional;

/**
 * Admin OIDC {@code client_id} must exist in {@code Clients}. YAML may still say {@code
 * skoruba4j-admin} after a SQL Server copy that already has the original Admin client.
 */
public final class AdminUiClientIds {
  private AdminUiClientIds() {}

  public static String resolve(String preferred, Optional<JdbcRepositories> jdbc) {
    String configured = preferred == null ? "" : preferred.trim();
    if (jdbc == null || jdbc.isEmpty()) {
      return configured;
    }
    ClientRepository clients = jdbc.get().clients();
    if (!configured.isBlank() && clients.findEnabledByClientId(configured).isPresent()) {
      return configured;
    }
    return clients.findEnabledAdminUiClientId().orElse(configured);
  }
}
