package com.myano.skoruba4j.adminapi.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Forwards {@code ?idp=whatsapp|wechat|google|microsoft} onto the STS authorize URL so STS login can
 * deep-link to that provider.
 */
public final class StsIdpAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
  private final OAuth2AuthorizationRequestResolver delegate;

  public StsIdpAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
    DefaultOAuth2AuthorizationRequestResolver inner =
        new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    inner.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    this.delegate = inner;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return enhance(request, delegate.resolve(request));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return enhance(request, delegate.resolve(request, clientRegistrationId));
  }

  private static OAuth2AuthorizationRequest enhance(
      HttpServletRequest request, OAuth2AuthorizationRequest original) {
    if (original == null || request == null) {
      return original;
    }
    String idp = normalizeIdp(request.getParameter("idp"));
    if (idp == null) {
      return original;
    }
    Map<String, Object> extra = new HashMap<>(original.getAdditionalParameters());
    extra.put("idp", idp);
    return OAuth2AuthorizationRequest.from(original).additionalParameters(extra).build();
  }

  static String normalizeIdp(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String v = raw.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "google", "microsoft", "whatsapp", "wechat" -> v;
      default -> null;
    };
  }
}
