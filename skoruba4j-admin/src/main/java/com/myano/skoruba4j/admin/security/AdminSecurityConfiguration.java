package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.admin.config.AdminLoginMode;
import com.myano.skoruba4j.admin.config.IdserverProperties;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AdminSecurityConfiguration {

  /** Loads Admin users from Identity tables for local password mode. */
  @Bean
  @Conditional(OnAdminLocalPasswordCondition.class)
  public UserDetailsService userDetailsService(Optional<JdbcRepositories> jdbc) {
    return new AdminIdentityUserDetailsService(jdbc);
  }

  /**
   * ASP.NET Identity PBKDF2 encoder so Boot's UserDetailsService auto-config does not use {@code
   * DelegatingPasswordEncoder} (which 500s on unprefixed hashes).
   */
  @Bean
  public PasswordEncoder passwordEncoder(IdentityPasswordHasher hasher) {
    return new IdentitySpringPasswordEncoder(hasher);
  }

  /** Local form login against Users; uses {@link IdentitySpringPasswordEncoder}. */
  @Bean
  @Conditional(OnAdminLocalPasswordCondition.class)
  public DaoAuthenticationProvider adminDaoAuthenticationProvider(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  /** Form login that validates via STS {@code grant_type=password}. */
  @Bean
  @Conditional(OnAdminStsPasswordCondition.class)
  public StsPasswordAuthenticationProvider stsPasswordAuthenticationProvider(
      StsPasswordLoginClient tokenClient, StsUserInfoClient userInfoClient) {
    return new StsPasswordAuthenticationProvider(tokenClient, userInfoClient);
  }

  /**
   * Single-provider manager for form login. Avoids Boot wiring UserDetailsService Dao with the
   * wrong encoder when multiple {@link org.springframework.security.authentication.AuthenticationProvider}
   * beans exist.
   */
  @Bean
  @Primary
  public AuthenticationManager authenticationManager(
      IdserverProperties properties,
      ObjectProvider<DaoAuthenticationProvider> adminDaoAuthenticationProvider,
      ObjectProvider<StsPasswordAuthenticationProvider> stsPasswordAuthenticationProvider) {
    AdminLoginMode mode = properties.loginMode();
    if (mode == AdminLoginMode.STS_PASSWORD) {
      return new ProviderManager(List.of(stsPasswordAuthenticationProvider.getObject()));
    }
    DaoAuthenticationProvider dao = adminDaoAuthenticationProvider.getIfAvailable();
    if (dao != null) {
      return new ProviderManager(List.of(dao));
    }
    return new ProviderManager(List.of(unusedProvider()));
  }

  /** HTTP security: form and/or OIDC according to {@link IdserverProperties#loginMode()}. */
  @Bean
  public SecurityFilterChain adminSecurityFilterChain(
      HttpSecurity http,
      IdserverProperties properties,
      AuthenticationManager authenticationManager,
      ObjectProvider<DaoAuthenticationProvider> adminDaoAuthenticationProvider,
      ObjectProvider<StsPasswordAuthenticationProvider> stsPasswordAuthenticationProvider,
      ObjectProvider<ClientRegistrationRepository> registrations,
      ObjectProvider<OAuth2UserService<OidcUserRequest, OidcUser>> oidcUserServices,
      ObjectProvider<OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>>
          tokenClients)
      throws Exception {
    String redirectPath = properties.redirectPath();
    AdminLoginMode mode = properties.loginMode();
    // Do not set http.authenticationManager(...) when OIDC is on — that replaces the chain
    // builder and drops OAuth2LoginAuthenticationProvider.
    if (mode.usesOidcClientBeans() && mode.showsPasswordForm()) {
      if (mode == AdminLoginMode.STS_PASSWORD
          && stsPasswordAuthenticationProvider.getIfAvailable() != null) {
        http.authenticationProvider(stsPasswordAuthenticationProvider.getObject());
      } else if (adminDaoAuthenticationProvider.getIfAvailable() != null) {
        http.authenticationProvider(adminDaoAuthenticationProvider.getObject());
      }
    } else if (mode.showsPasswordForm()) {
      http.authenticationManager(authenticationManager);
    }
    http.authorizeHttpRequests(
        authorize ->
            authorize
                .requestMatchers(
                    "/health",
                    "/error",
                    "/login",
                    "/logout",
                    "/lang",
                    "/about",
                    "/signout-callback-oidc",
                    redirectPath,
                    "/oauth2/**")
                .permitAll()
                .anyRequest()
                .access(
                    (authentication, context) -> {
                      var current = authentication.get();
                      return new AuthorizationDecision(
                          current != null
                              && current.isAuthenticated()
                              && AdminRole.hasRole(
                                  current.getAuthorities(), properties.adminRole()));
                    }));
    AdminRoleSuccessHandler signedIn = new AdminRoleSuccessHandler(properties.adminRole());
    if (mode.showsPasswordForm()) {
      http.formLogin(
          form ->
              form.loginPage("/login")
                  .loginProcessingUrl("/login")
                  .successHandler(signedIn)
                  .failureUrl("/login?error")
                  .permitAll());
    }
    if (mode.usesOidcClientBeans() && registrations.getIfAvailable() != null) {
      http.oauth2Login(
          oauth -> {
            var resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                    registrations.getObject(), "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());
            oauth
                .loginPage("/login")
                .failureHandler(new OidcLoginFailureHandler())
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .redirectionEndpoint(redirection -> redirection.baseUri(redirectPath))
                .tokenEndpoint(token -> token.accessTokenResponseClient(tokenClients.getObject()))
                .successHandler(signedIn)
                .userInfoEndpoint(
                    userInfo -> userInfo.oidcUserService(oidcUserServices.getObject()));
          });
    }
    // Prefer silent STS SSO when OIDC client is wired. Password form stays at explicit /login
    // (and OIDC failure handler). Avoids forcing a click on "Sign in with STS" after Admin
    // session loss while the STS cookie is still valid (e.g. STS restart).
    String entry =
        mode.usesOidcClientBeans() ? "/oauth2/authorization/sts" : "/login";
    LoginUrlAuthenticationEntryPoint login = new LoginUrlAuthenticationEntryPoint(entry);
    http.exceptionHandling(
        exceptions -> {
          exceptions.authenticationEntryPoint(login);
          exceptions.accessDeniedHandler(
              (request, response, denied) -> {
                var auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
                new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                    .logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?denied=1");
              });
        });
    http.logout(
        logout ->
            logout
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                .logoutSuccessHandler(
                    AdminLogout.successHandler(properties, registrations.getIfAvailable())));
    return http.build();
  }

  /** OIDC-only mode has no form AuthenticationProvider; ProviderManager forbids an empty list. */
  private static AuthenticationProvider unusedProvider() {
    return new AuthenticationProvider() {
      @Override
      public org.springframework.security.core.Authentication authenticate(
          org.springframework.security.core.Authentication authentication) {
        return null;
      }

      @Override
      public boolean supports(Class<?> authentication) {
        return false;
      }
    };
  }

  @Bean
  @Conditional(OnAdminStsOidcCondition.class)
  public ClientRegistrationRepository clientRegistrationRepository(
      IdserverProperties properties, Optional<JdbcRepositories> jdbc) {
    return new InMemoryClientRegistrationRepository(
        StsClientRegistrationFactory.create(properties, jdbc));
  }

  @Bean
  @Conditional(OnAdminStsOidcCondition.class)
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
      authorizationCodeTokenResponseClient(RestTemplate stsOutboundRestTemplate) {
    DefaultAuthorizationCodeTokenResponseClient client =
        new DefaultAuthorizationCodeTokenResponseClient();
    client.setRestOperations(stsOutboundRestTemplate);
    return client;
  }

  @Bean
  @Conditional(OnAdminStsOidcCondition.class)
  public JwtDecoderFactory<ClientRegistration> oidcIdTokenDecoderFactory(
      RestTemplate stsOutboundRestTemplate) {
    return registration -> {
      String jwkSetUri = registration.getProviderDetails().getJwkSetUri();
      NimbusJwtDecoder decoder =
          NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
              .restOperations(stsOutboundRestTemplate)
              .build();
      OAuth2TokenValidator<Jwt> withIssuer =
          JwtValidators.createDefaultWithIssuer(registration.getProviderDetails().getIssuerUri());
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(withIssuer, new OidcIdTokenValidator(registration)));
      return decoder;
    };
  }

  @Bean
  @Conditional(OnAdminStsOidcCondition.class)
  public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(
      Optional<JdbcRepositories> jdbc, RestTemplate stsOutboundRestTemplate) {
    DefaultOAuth2UserService oauth2UserService = new DefaultOAuth2UserService();
    oauth2UserService.setRestOperations(stsOutboundRestTemplate);
    OidcUserService delegate = new OidcUserService();
    delegate.setOauth2UserService(oauth2UserService);
    return request -> {
      OidcUser user = delegate.loadUser(request);
      var authorities =
          AdminOidcAuthorities.merge(
              user.getAuthorities(),
              user.getClaim("role"),
              user.getClaim("roles"),
              user.getSubject(),
              jdbc);
      authorities =
          AdminOidcAuthorities.merge(
              authorities,
              request.getIdToken().getClaim("role"),
              request.getIdToken().getClaim("roles"),
              user.getSubject(),
              jdbc);
      if (user.getUserInfo() != null) {
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
      }
      return new DefaultOidcUser(authorities, user.getIdToken());
    };
  }
}
