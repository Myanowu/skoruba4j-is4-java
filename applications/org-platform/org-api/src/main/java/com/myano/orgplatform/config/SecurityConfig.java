package com.myano.orgplatform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String LOGIN = "/oauth2/authorization/sts";

  private final OrgPlatformProperties properties;

  public SecurityConfig(OrgPlatformProperties properties) {
    this.properties = properties;
  }

  /** STS authorization-code client. Absent while {@code orgplatform.security.mode=open}. */
  @Bean
  @ConditionalOnProperty(name = "orgplatform.security.mode", havingValue = "oidc")
  ClientRegistrationRepository clientRegistrationRepository() {
    return new InMemoryClientRegistrationRepository(
        StsLoginClient.create(properties.getSecurity()));
  }

  /** Browser session against STS, or fully open when mode is not {@code oidc}. */
  @Bean
  @Order(1)
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> clients)
      throws Exception {
    http.csrf(csrf -> csrf.disable());
    if (properties.getSecurity().isOpen()) {
      http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
      return http.build();
    }
    ClientRegistrationRepository registrations = clients.getObject();
    String adminRole = properties.getSecurity().getAdminRole();
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/health", "/error", "/login/denied", "/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    .anyRequest()
                    .access(
                        (authentication, context) -> {
                          Authentication current = authentication.get();
                          return new AuthorizationDecision(
                              current != null
                                  && current.isAuthenticated()
                                  && OrgAdminRole.hasRole(current.getAuthorities(), adminRole));
                        }))
        .oauth2Login(
            oauth -> {
              var resolver =
                  new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
              resolver.setAuthorizationRequestCustomizer(
                  OAuth2AuthorizationRequestCustomizers.withPkce());
              oauth
                  .loginPage(LOGIN)
                  .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                  .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
                  .failureHandler(
                      (request, response, exception) ->
                          response.sendRedirect(request.getContextPath() + "/login/denied?error=1"));
            })
        .exceptionHandling(
            exceptions -> {
              exceptions.authenticationEntryPoint(loginEntryPoint());
              exceptions.accessDeniedHandler(
                  (request, response, denied) -> deny(request, response));
            })
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/logout"))
                    .logoutSuccessHandler(logoutSuccess(registrations)));
    return http.build();
  }

  /** Reads the STS {@code role} claim onto the session principal. */
  private static OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
    OidcUserService delegate = new OidcUserService();
    return request -> {
      OidcUser user = delegate.loadUser(request);
      var authorities =
          OrgAdminRole.merge(user.getAuthorities(), user.getClaim("role"), user.getClaim("roles"));
      authorities =
          OrgAdminRole.merge(
              authorities,
              request.getIdToken().getClaim("role"),
              request.getIdToken().getClaim("roles"));
      if (user.getUserInfo() != null) {
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
      }
      return new DefaultOidcUser(List.copyOf(authorities), user.getIdToken());
    };
  }

  /** Sends the browser to STS. API calls get JSON 401 so {@code fetch} does not follow HTML. */
  private static AuthenticationEntryPoint loginEntryPoint() {
    LoginUrlAuthenticationEntryPoint login = new LoginUrlAuthenticationEntryPoint(LOGIN);
    return (request, response, authException) -> {
      if (isApi(request)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"請先登入\"}");
        return;
      }
      login.commence(request, response, authException);
    };
  }

  /** Clears a session that lacks the admin role, then explains why. */
  private static void deny(HttpServletRequest request, HttpServletResponse response)
      throws java.io.IOException {
    if (isApi(request)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"error\":\"需要 STS 管理角色\"}");
      return;
    }
    Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    new SecurityContextLogoutHandler().logout(request, response, auth);
    response.sendRedirect(request.getContextPath() + "/login/denied");
  }

  private static OidcClientInitiatedLogoutSuccessHandler logoutSuccess(
      ClientRegistrationRepository registrations) {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(registrations);
    handler.setPostLogoutRedirectUri("{baseUrl}/");
    return handler;
  }

  private static boolean isApi(HttpServletRequest request) {
    String path = request.getRequestURI();
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && path.startsWith(context)) {
      path = path.substring(context.length());
    }
    return path.startsWith("/api/");
  }
}
