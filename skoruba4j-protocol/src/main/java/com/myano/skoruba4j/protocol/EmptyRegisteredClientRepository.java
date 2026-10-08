package com.myano.skoruba4j.protocol;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** OSS without a database: no Clients rows. SAS InMemory repository forbids an empty list. */
public final class EmptyRegisteredClientRepository implements RegisteredClientRepository {
  @Override
  public void save(RegisteredClient registeredClient) {
    throw new UnsupportedOperationException("no database");
  }

  @Override
  public RegisteredClient findById(String id) {
    return null;
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    return null;
  }
}
