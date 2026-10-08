package com.myano.skoruba4j.protocol;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.jdbc.UncheckedSqlException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OidcLogoutEndpointConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.OidcProviderConfiguration;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
public class AuthorizationServerConfiguration {

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http,
      IdentityOidcUserInfoMapper userInfoMapper,
      DelegationGrantAuthenticationConverter delegationConverter,
      DelegationGrantAuthenticationProvider delegationProvider,
      PasswordGrantAuthenticationConverter passwordConverter,
      PasswordGrantAuthenticationProvider passwordProvider,
      Is4PublicClientAuthenticationConverter publicClientAuthenticationConverter,
      Is4PublicClientAuthenticationProvider publicClientAuthenticationProvider,
      Is4OidcLogoutAuthenticationProvider oidcLogoutAuthenticationProvider,
      JwtDecoder jwtDecoder,
      RegisteredClientRepository registeredClientRepository,
      OAuth2AuthorizationService authorizationService,
      Optional<JdbcRepositories> jdbc,
      SecurityContextRepository securityContextRepository,
      @Value("${idserver.logout.end-session:compatible}") String endSessionMode)
      throws Exception {
    EndSessionMode logoutMode = EndSessionMode.fromConfig(endSessionMode);
    OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
        new OAuth2AuthorizationServerConfigurer();
    http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
        .with(
            authorizationServerConfigurer,
            as ->
                as.clientAuthentication(
                        clientAuth ->
                            clientAuth
                                .authenticationConverter(publicClientAuthenticationConverter)
                                // Before SAS PublicClientAuthenticationProvider (PKCE-required).
                                .authenticationProvider(publicClientAuthenticationProvider))
                    .tokenEndpoint(
                        token ->
                            token
                                .accessTokenRequestConverter(delegationConverter)
                                .accessTokenRequestConverter(passwordConverter)
                                .authenticationProvider(delegationProvider)
                                .authenticationProvider(passwordProvider))
                    .oidc(
                        oidc ->
                            oidc.userInfoEndpoint(userInfo -> userInfo.userInfoMapper(userInfoMapper))
                                .logoutEndpoint(
                                    logout ->
                                        configureEndSession(
                                            logout,
                                            logoutMode,
                                            oidcLogoutAuthenticationProvider,
                                            jwtDecoder,
                                            registeredClientRepository,
                                            authorizationService))
                                .providerConfigurationEndpoint(
                                    pc ->
                                        pc.providerConfigurationCustomizer(
                                            oidcDiscoveryCustomizer(jdbc))))
                    .authorizationEndpoint(
                        endpoint -> endpoint.errorResponseHandler(new Is4AuthorizeErrorHandler())))
        .oauth2ResourceServer(
            resource ->
                resource
                    .jwt(Customizer.withDefaults())
                    .bearerTokenResolver(new BrowserAuthorizeBearerTokenResolver()))
        .securityContext(
            context ->
                context
                    .securityContextRepository(securityContextRepository)
                    .requireExplicitSave(false))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            authorize -> {
              authorize
                  .requestMatchers(
                      Is4Paths.JWKS,
                      Is4Paths.OAUTH2_JWKS,
                      Is4Paths.DISCOVERY,
                      "/.well-known/**")
                  .permitAll();
              if (logoutMode.isCompatible()) {
                authorize.requestMatchers(Is4Paths.END_SESSION).permitAll();
              }
              authorize.anyRequest().authenticated();
            })
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new Is4LoginAuthenticationEntryPoint(),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    http.requestCache(cache -> cache.requestCache(httpSessionRequestCache()));
    return http.build();
  }

  /**
   * Runs just before Spring Security so browser {@code /connect/authorize} can offer the account
   * chooser before SAS issues a code (SAS endpoint filter has no registered HttpSecurity order).
   */
  @Bean
  public FilterRegistrationBean<AccountChooserAuthorizeFilter> accountChooserAuthorizeFilter() {
    FilterRegistrationBean<AccountChooserAuthorizeFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new AccountChooserAuthorizeFilter());
    registration.addUrlPatterns(Is4Paths.AUTHORIZE);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }

  /**
   * Prefer encrypted auth cookie (survives STS restart, C# Identity cookie analogue), then session
   * for OAuth round-trip attributes.
   */
  @Bean
  public SecurityContextRepository securityContextRepository(
      @Value("${idserver.auth-cookie.key-file:secrets/sts-auth.key}") String authKeyFile,
      @Value("${idserver.auth-cookie.name:SKORUBA4J_STS_AUTH}") String authCookieName,
      @Value("${idserver.auth-cookie.ttl:10h}") Duration authTtl,
      @Value("${server.ssl.enabled:false}") boolean sslEnabled) {
    Path keyPath = IdServerHome.resolve(authKeyFile);
    AuthCookieCipher cipher = AuthCookieCipher.loadOrCreate(keyPath);
    EncryptedCookieSecurityContextRepository cookieRepo =
        new EncryptedCookieSecurityContextRepository(cipher, authCookieName, authTtl, sslEnabled);
    return new DelegatingSecurityContextRepository(
        new RequestAttributeSecurityContextRepository(),
        cookieRepo,
        new HttpSessionSecurityContextRepository());
  }

  @Bean
  public RequestCache httpSessionRequestCache() {
    HttpSessionRequestCache cache = new HttpSessionRequestCache();
    cache.setMatchingRequestParameterName(null);
    return cache;
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings(
      @Value("${idserver.issuer-uri:}") String issuer) {
    return Is4AuthorizationServerSettings.create(issuer);
  }

  @Bean
  public RegisteredClientRepository registeredClientRepository(
      Optional<JdbcRepositories> jdbc,
      @Value("${idserver.logout.end-session:compatible}") String endSessionMode) {
    EndSessionMode mode = EndSessionMode.fromConfig(endSessionMode);
    return jdbc.<RegisteredClientRepository>map(j -> new Is4RegisteredClientRepository(j, mode))
        .orElseGet(EmptyRegisteredClientRepository::new);
  }

  /**
   * Prefer IS4 {@code PersistedGrants} (same table C# operational store uses). Fall back to memory
   * when JDBC is not configured.
   */
  @Bean
  public OAuth2AuthorizationService authorizationService(
      Optional<JdbcRepositories> jdbc, RegisteredClientRepository registeredClientRepository) {
    return jdbc.<OAuth2AuthorizationService>map(
            repos ->
                new PersistedGrantOAuth2AuthorizationService(
                    repos.persistedGrants(), registeredClientRepository))
        .orElseGet(InMemoryOAuth2AuthorizationService::new);
  }

  @Bean
  public Is4OidcLogoutAuthenticationProvider is4OidcLogoutAuthenticationProvider(
      JwtDecoder jwtDecoder, RegisteredClientRepository registeredClientRepository) {
    return new Is4OidcLogoutAuthenticationProvider(jwtDecoder, registeredClientRepository);
  }

  @Bean
  public IdentityOidcUserInfoMapper identityOidcUserInfoMapper(Optional<JdbcRepositories> jdbc) {
    return new IdentityOidcUserInfoMapper(jdbc);
  }

  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(
      Optional<JdbcRepositories> jdbc, RSAKey rsaSigningKey) {
    ApiResourceAudienceCustomizer audience =
        jdbc.map(j -> new ApiResourceAudienceCustomizer(j.audiences()))
            .orElseGet(() -> new ApiResourceAudienceCustomizer(null));
    IdentityTokenClaimsCustomizer identity = new IdentityTokenClaimsCustomizer(jdbc);
    return context -> {
      JwsHeaderKidCustomizer.apply(context, rsaSigningKey.getKeyID());
      audience.customize(context);
      identity.customize(context);
    };
  }

  @Bean
  public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource, RSAKey rsaSigningKey) {
    return new KidJwtEncoder(new NimbusJwtEncoder(jwkSource), rsaSigningKey.getKeyID());
  }

  @Bean
  public OAuth2TokenGenerator<?> tokenGenerator(
      JwtEncoder jwtEncoder, OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
    JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
    jwtGenerator.setJwtCustomizer(jwtCustomizer);
    return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2RefreshTokenGenerator());
  }

  @Bean
  public FilterRegistrationBean<PublicJwkSetFilter> publicJwkSetFilter(
      JWKSource<SecurityContext> jwkSource) {
    FilterRegistrationBean<PublicJwkSetFilter> registration =
        new FilterRegistrationBean<>(new PublicJwkSetFilter(jwkSource));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setName("publicJwkSetFilter");
    return registration;
  }

  @Bean
  public DelegationGrantAuthenticationConverter delegationGrantAuthenticationConverter() {
    return new DelegationGrantAuthenticationConverter();
  }

  @Bean
  public DelegationGrantAuthenticationProvider delegationGrantAuthenticationProvider(
      OAuth2AuthorizationService authorizationService,
      OAuth2TokenGenerator<?> tokenGenerator,
      JwtDecoder jwtDecoder) {
    return new DelegationGrantAuthenticationProvider(
        authorizationService, tokenGenerator, jwtDecoder);
  }

  @Bean
  public PasswordGrantAuthenticationConverter passwordGrantAuthenticationConverter() {
    return new PasswordGrantAuthenticationConverter();
  }

  /** Public client_id auth for password/delegation grants (SAS PKCE converter skips these). */
  @Bean
  public Is4PublicClientAuthenticationConverter is4PublicClientAuthenticationConverter() {
    return new Is4PublicClientAuthenticationConverter();
  }

  /**
   * Public client authentication for password/delegation without PKCE (see class javadoc — SAS
   * otherwise returns invalid_grant on code_verifier).
   */
  @Bean
  public Is4PublicClientAuthenticationProvider is4PublicClientAuthenticationProvider(
      RegisteredClientRepository registeredClientRepository) {
    return new Is4PublicClientAuthenticationProvider(registeredClientRepository);
  }

  /** Shared-secret hasher for confidential clients (not for Users.PasswordHash). */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new Is4ClientSecretPasswordEncoder();
  }

  /**
   * Stable signing key on disk (IS4 {@code tempkey.jwk} / {@code AddDeveloperSigningCredential}).
   * Restart keeps the same {@code kid} so existing JWTs still verify against JWKS.
   */
  @Bean
  public RSAKey rsaSigningKey(
      @Value("${idserver.signing.jwk-file:secrets/sts-signing.jwk}") String jwkFile) {
    return SigningRsaKey.loadOrCreate(IdServerHome.resolve(jwkFile));
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource(RSAKey rsaSigningKey) {
    return new ImmutableJWKSet<>(new JWKSet(rsaSigningKey));
  }

  @Bean
  public JwtDecoder jwtDecoder(
      JWKSource<SecurityContext> jwkSource,
      @Value("${idserver.issuer-uri:}") String issuer,
      @Value("${idserver.logout.end-session:compatible}") String endSessionMode) {
    JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    if (decoder instanceof NimbusJwtDecoder nimbus) {
      nimbus.setJwtValidator(
          JwtExpiryValidators.create(issuer, EndSessionMode.fromConfig(endSessionMode)));
    }
    return decoder;
  }

  static Consumer<OidcProviderConfiguration.Builder> oidcDiscoveryCustomizer(
      Optional<JdbcRepositories> jdbc) {
    return builder -> {
      List<String> fromDb = List.of();
      if (jdbc.isPresent()) {
        try {
          fromDb = jdbc.get().scopes().listDiscoveryNames();
        } catch (UncheckedSqlException ignored) {
          // discovery still advertises OIDC defaults
        }
      }
      List<String> scopesToAdvertise = DiscoveryScopes.merge(fromDb);
      builder.scopes(scopes -> scopes.addAll(scopesToAdvertise));
      builder.claim("frontchannel_logout_supported", true);
      builder.claim("frontchannel_logout_session_supported", true);
    };
  }

  static void configureEndSession(
      OidcLogoutEndpointConfigurer logout,
      EndSessionMode mode,
      Is4OidcLogoutAuthenticationProvider compatibleProvider,
      JwtDecoder jwtDecoder,
      RegisteredClientRepository clients,
      OAuth2AuthorizationService authorizations) {
    logout.logoutResponseHandler(Is4LogoutResponseHandler.create());
    if (mode.isCompatible()) {
      logout.authenticationProviders(
          providers -> {
            providers.clear();
            providers.add(compatibleProvider);
          });
      return;
    }
    var sas = new OidcLogoutAuthenticationProvider(clients, authorizations, new SessionRegistryImpl());
    logout.authenticationProviders(
        providers -> {
          providers.clear();
          providers.add(new JwtExpiryLogoutAuthenticationProvider(jwtDecoder, sas));
        });
  }
}
