package com.myano.skoruba4j.adminapi.security;

import com.myano.skoruba4j.adminapi.config.ApiLoginMode;
import com.myano.skoruba4j.adminapi.config.IdserverProperties;
import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.tls.OutboundTrust;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AdminApiSecurityConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public UserDetailsService uiUserDetailsService(Optional<JdbcRepositories> jdbc) {
    return new UiIdentityUserDetailsService(jdbc);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public PasswordEncoder passwordEncoder(IdentityPasswordHasher hasher) {
    return new IdentitySpringPasswordEncoder(hasher);
  }

  @Bean
  @Order(1)
  public SecurityFilterChain adminApiJwtSecurityFilterChain(
      HttpSecurity http, IdserverProperties properties, JwtDecoder jwtDecoder) throws Exception {
    String adminRole = properties.adminRole();
    SessionCreationPolicy sessions =
        properties.apiUiEnabled()
            ? SessionCreationPolicy.IF_REQUIRED
            : SessionCreationPolicy.STATELESS;
    http.securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(sessions))
        .authorizeHttpRequests(
            authorize -> authorize.anyRequest().access(AdminApiAccess.requireAdminRole(adminRole)))
        .oauth2ResourceServer(
            oauth ->
                oauth.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  @Bean
  @Order(2)
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public SecurityFilterChain adminApiUiSecurityFilterChain(
      HttpSecurity http,
      IdserverProperties properties,
      ObjectProvider<UserDetailsService> uiUserDetailsService,
      ObjectProvider<PasswordEncoder> passwordEncoder,
      ObjectProvider<ClientRegistrationRepository> registrations,
      ObjectProvider<OAuth2UserService<OidcUserRequest, OidcUser>> oidcUserServices,
      ObjectProvider<OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>>
          tokenClients)
      throws Exception {
    String adminRole = properties.adminRole();
    ApiLoginMode mode = properties.apiLoginMode();
    String redirectPath = properties.apiRedirectPath();
    ApiRoleSuccessHandler signedIn = new ApiRoleSuccessHandler(adminRole);
    http.securityMatcher("/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/health",
                        "/error",
                        "/login",
                        "/logout",
                        "/signout-callback-oidc",
                        redirectPath,
                        "/oauth2/**",
                        "/css/**")
                    .permitAll()
                    .anyRequest()
                    .access(AdminApiAccess.requireAdminRole(adminRole)));
    if (mode.showsPasswordForm()
        && uiUserDetailsService.getIfAvailable() != null
        && passwordEncoder.getIfAvailable() != null) {
      // Register Dao on the chain builder. http.authenticationManager(...) would drop
      // oauth2Login's OAuth2LoginAuthenticationProvider (ProviderNotFoundException).
      DaoAuthenticationProvider dao =
          new DaoAuthenticationProvider(uiUserDetailsService.getObject());
      dao.setPasswordEncoder(passwordEncoder.getObject());
      http.authenticationProvider(dao);
      http.formLogin(
          form ->
              form.loginPage("/login")
                  .loginProcessingUrl("/login")
                  .successHandler(signedIn)
                  .failureUrl("/login?error")
                  .permitAll());
    }
    if (mode.usesStsOidc()) {
      http.oauth2Login(
          oauth -> {
            var resolver = new StsIdpAuthorizationRequestResolver(registrations.getObject());
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
    String entry = mode == ApiLoginMode.STS_OIDC ? "/oauth2/authorization/sts" : "/login";
    http.exceptionHandling(
        ex -> {
          ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(entry));
          // Signed-in but missing admin role: clear session, never show API console.
          ex.accessDeniedHandler(
              (request, response, denied) -> {
                var auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?denied=1");
              });
        });
    http.logout(
        logout ->
            logout
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                .logoutSuccessHandler(
                    ApiUiLogout.successHandler(properties, registrations.getIfAvailable())));
    return http.build();
  }

  @Bean
  @Order(2)
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "false")
  public SecurityFilterChain adminApiNoUiSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ClientRegistrationRepository clientRegistrationRepository(
      IdserverProperties properties, Optional<JdbcRepositories> jdbc) {
    return new InMemoryClientRegistrationRepository(
        StsClientRegistrationFactory.create(properties, jdbc));
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
      authorizationCodeTokenResponseClient(RestTemplate stsOutboundRestTemplate) {
    DefaultAuthorizationCodeTokenResponseClient client =
        new DefaultAuthorizationCodeTokenResponseClient();
    client.setRestOperations(stsOutboundRestTemplate);
    return client;
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
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
  @ConditionalOnProperty(
      prefix = "idserver.admin",
      name = "api-ui-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(
      Optional<JdbcRepositories> jdbc, RestTemplate stsOutboundRestTemplate) {
    DefaultOAuth2UserService oauth2UserService = new DefaultOAuth2UserService();
    oauth2UserService.setRestOperations(stsOutboundRestTemplate);
    OidcUserService delegate = new OidcUserService();
    delegate.setOauth2UserService(oauth2UserService);
    return request -> {
      OidcUser user = delegate.loadUser(request);
      var authorities =
          UiOidcAuthorities.merge(
              user.getAuthorities(),
              user.getClaim("role"),
              user.getClaim("roles"),
              user.getSubject(),
              jdbc);
      authorities =
          UiOidcAuthorities.merge(
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

  @Bean
  public JwtDecoder jwtDecoder(
      IdserverProperties properties,
      @Value("${server.ssl.key-store:}") String serverKeyStore,
      @Value("${server.ssl.key-store-password:}") String serverKeyStorePassword,
      @Value("${server.ssl.key-store-type:}") String serverKeyStoreType) {
    String issuer = properties.issuerUri();
    String jwks = issuer + "/.well-known/openid-configuration/jwks";
    HttpClient http =
        HttpClient.newBuilder()
            .sslContext(
                OutboundTrust.sslContext(
                    properties.tlsTrustStore(),
                    properties.tlsTrustStorePassword(),
                    properties.tlsTrustStoreType(),
                    serverKeyStore,
                    serverKeyStorePassword,
                    serverKeyStoreType))
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    RestTemplate rest = new RestTemplate(new JdkClientHttpRequestFactory(http));
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(jwks).restOperations(rest).build();
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
    if (properties.strictTokens()) {
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              withIssuer, new JwtTimestampValidator(Duration.ZERO)));
    } else {
      decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer));
    }
    return decoder;
  }

  static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(AdminApiJwtAuthorities::from);
    return converter;
  }
}
