package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.configstore.ClientRepository;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public final class Is4RegisteredClientRepository implements RegisteredClientRepository {
  private static final Logger log = LoggerFactory.getLogger(Is4RegisteredClientRepository.class);
  private final ClientRepository clients;
  private final EndSessionMode mode;

  public Is4RegisteredClientRepository(JdbcRepositories jdbc) {
    this(jdbc, EndSessionMode.COMPATIBLE);
  }

  public Is4RegisteredClientRepository(JdbcRepositories jdbc, EndSessionMode mode) {
    this.clients = jdbc.clients();
    this.mode = mode == null ? EndSessionMode.COMPATIBLE : mode;
  }

  @Override
  public void save(RegisteredClient registeredClient) {
    throw new UnsupportedOperationException("Clients are read from IS4 Clients table");
  }

  @Override
  public RegisteredClient findById(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    try {
      RegisteredClient byClientId = map(clients.findEnabledByClientId(id));
      if (byClientId != null) {
        return byClientId;
      }
      return map(clients.findEnabledByPk(Integer.parseInt(id)));
    } catch (NumberFormatException e) {
      return null;
    } catch (UncheckedSqlException ex) {
      log.error("Clients lookup failed for id {}", id, ex);
      return null;
    }
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    try {
      return map(clients.findEnabledByClientId(clientId));
    } catch (UncheckedSqlException ex) {
      log.error("Clients lookup failed for {}", clientId, ex);
      return null;
    }
  }

  private RegisteredClient map(
      java.util.Optional<com.myano.skoruba4j.domain.configstore.ClientConfiguration> config) {
    return config
        .map(
            c -> {
              try {
                return RegisteredClientMapper.toRegisteredClient(c, Instant.now(), mode);
              } catch (RuntimeException ex) {
                log.warn("Could not map IS4 client {}: {}", c.clientId(), ex.toString());
                return null;
              }
            })
        .orElse(null);
  }
}
