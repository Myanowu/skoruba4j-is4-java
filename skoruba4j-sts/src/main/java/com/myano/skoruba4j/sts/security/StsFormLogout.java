package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.protocol.PostLogoutRedirects;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** After form logout, return to the RP that asked, otherwise the STS login page. */
public final class StsFormLogout {
  private StsFormLogout() {}

  public static String target(
      RegisteredClientRepository clients, String clientId, String postLogoutRedirectUri) {
    RegisteredClient client = null;
    if (clients != null && clientId != null && !clientId.isBlank()) {
      client = clients.findByClientId(clientId.trim());
    }
    if (PostLogoutRedirects.allowed(client, postLogoutRedirectUri)) {
      return postLogoutRedirectUri;
    }
    return "/login?logout";
  }
}
