package com.myano.skoruba4j.sts.security.externallogin;

import com.myano.skoruba4j.domain.externallogin.ExternalLoginClientSettings;
import com.myano.skoruba4j.domain.externallogin.ExternalLoginLinkMode;
import com.myano.skoruba4j.domain.identity.IdentityUser;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.IdentityUserDetailsService;
import com.myano.skoruba4j.sts.security.Is4LoginSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * After Google/Microsoft OIDC, apply per-Client link mode and establish the same Identity {@link
 * UserDetails} principal as form login.
 */
public final class ExternalOidcLoginSuccessHandler implements AuthenticationSuccessHandler {
  private final ExternalIdentityLinker linker;
  private final IdentityUserDetailsService users;
  private final IdserverProperties props;
  private final Is4LoginSuccessHandler resume;
  private final AuthenticationSuccessHandler rejectRedirect =
      (request, response, authentication) ->
          response.sendRedirect("/login?error=external-no-account");

  public ExternalOidcLoginSuccessHandler(
      ExternalIdentityLinker linker,
      IdentityUserDetailsService users,
      IdserverProperties props,
      RequestCache requestCache,
      SecurityContextRepository securityContextRepository) {
    this.linker = linker;
    this.users = users;
    this.props = props;
    this.resume = new Is4LoginSuccessHandler(securityContextRepository);
    this.resume.setRequestCache(requestCache);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      response.sendRedirect("/login?error=external");
      return;
    }
    String registrationId = registrationId(authentication);
    String clientId = AuthorizeClientIds.resolve(request).orElse(null);
    ExternalLoginClientSettings settings = linker.settingsForClient(clientId);
    boolean allow = allowProvider(registrationId, settings, clientId);
    if (!allow) {
      SecurityContextHolder.clearContext();
      response.sendRedirect("/login?error=external-disabled");
      return;
    }
    ExternalLoginLinkMode mode =
        clientId == null ? ExternalLoginLinkMode.LINK_EXISTING : linkMode(registrationId, settings);
    ExternalLoginLinkResult result = linker.linkExternal(registrationId, oidcUser, mode);
    if (result instanceof ExternalLoginLinkResult.NeedConfirm need) {
      SecurityContextHolder.clearContext();
      ExternalLoginSession.setPending(request, need.pending());
      response.sendRedirect("/external/confirm");
      return;
    }
    if (result instanceof ExternalLoginLinkResult.Rejected rejected) {
      SecurityContextHolder.clearContext();
      response.sendRedirect(
          "/login?error=" + URLEncoder.encode(rejected.messageKey(), StandardCharsets.UTF_8));
      return;
    }
    if (!(result instanceof ExternalLoginLinkResult.Authenticated ok)) {
      SecurityContextHolder.clearContext();
      rejectRedirect.onAuthenticationSuccess(request, response, authentication);
      return;
    }
    IdentityUser user = ok.user();
    UserDetails details = users.userDetailsFor(user);
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            details, details.getPassword(), details.getAuthorities());
    resume.onAuthenticationSuccess(request, response, token);
  }

  private boolean allowProvider(
      String registrationId, ExternalLoginClientSettings settings, String clientId) {
    if (ExternalIdentityLinker.GOOGLE.equals(registrationId)) {
      return props.googleLoginConfigured()
          && (settings.googleEnabled()
              || (clientId == null
                  && props.getExternalLogin().getGoogle().isAllowDirectLogin()));
    }
    if (ExternalIdentityLinker.MICROSOFT.equals(registrationId)) {
      return props.microsoftLoginConfigured()
          && (settings.microsoftEnabled()
              || (clientId == null
                  && props.getExternalLogin().getMicrosoft().isAllowDirectLogin()));
    }
    return false;
  }

  private static ExternalLoginLinkMode linkMode(
      String registrationId, ExternalLoginClientSettings settings) {
    if (ExternalIdentityLinker.MICROSOFT.equals(registrationId)) {
      return settings.microsoftLinkMode();
    }
    return settings.googleLinkMode();
  }

  private static String registrationId(Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken oauth) {
      return oauth.getAuthorizedClientRegistrationId();
    }
    return "";
  }
}
