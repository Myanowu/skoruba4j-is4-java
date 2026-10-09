package com.myano.skoruba4j.sts.security;

import com.myano.skoruba4j.domain.jdbc.JdbcRepositories;
import com.myano.skoruba4j.domain.password.IdentityPasswordHasher;
import com.myano.skoruba4j.protocol.Is4Paths;
import com.myano.skoruba4j.protocol.Is4ReturnUrls;
import com.myano.skoruba4j.sts.config.IdserverProperties;
import com.myano.skoruba4j.sts.security.externallogin.ExternalIdentityLinker;
import com.myano.skoruba4j.sts.security.externallogin.ExternalOidcLoginSuccessHandler;
import jakarta.servlet.DispatcherType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class FormLoginSecurityConfiguration {
  private static final Logger log = LoggerFactory.getLogger(FormLoginSecurityConfiguration.class);

  @Bean
  public IdentityUserDetailsService identityUserDetailsService(Optional<JdbcRepositories> jdbc) {
    return new IdentityUserDetailsService(jdbc);
  }

  @Bean
  public UserDetailsService userDetailsService(IdentityUserDetailsService identity) {
    return identity;
  }

  /**
   * Form / ROPC password verify (ASP.NET PBKDF2 + optional Control debug password). Not the OAuth
   * client-secret encoder.
   */
  @Bean
  public IdentitySpringPasswordEncoder identityLoginPasswordEncoder(
      IdentityPasswordHasher hasher, IdserverProperties props) {
    IdentitySpringPasswordEncoder encoder =
        IdentitySpringPasswordEncoder.withOptionalDebug(
            hasher, props.debugLoginEnabled(), props.debugLoginPassword());
    if (encoder.debugLoginEnabled()) {
      log.warn(
          "STS login.debug-mode is ON: any existing user can sign in with the Control debug password. Turn off before production.");
    }
    return encoder;
  }

  /**
   * Identity-user AuthenticationManager (ASP.NET PBKDF2). Marked {@link Primary} so password-grant
   * injection does not pick Boot's UserDetailsService manager (which uses the client-secret {@link
   * org.springframework.security.crypto.password.PasswordEncoder} bean and always fails ROPC).
   */
  @Bean(name = "stsUserAuthenticationManager")
  @Primary
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, IdentitySpringPasswordEncoder identityLoginPasswordEncoder) {
    return formLoginAuthenticationManager(userDetailsService, identityLoginPasswordEncoder);
  }

  @Bean
  @Order(2)
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http,
      RequestCache requestCache,
      SecurityContextRepository securityContextRepository,
      RegisteredClientRepository registeredClientRepository,
      IdserverProperties props,
      ExternalIdentityLinker linker,
      IdentityUserDetailsService identityUserDetailsService,
      IdentitySpringPasswordEncoder identityLoginPasswordEncoder)
      throws Exception {
    // Register Dao provider on the filter-chain builder (not a shared AuthenticationManager).
    // Setting http.authenticationManager(formOnly) drops oauth2Login's
    // OAuth2LoginAuthenticationProvider → ProviderNotFoundException on Google callback.
    http.authenticationProvider(
            identityDaoAuthenticationProvider(
                identityUserDetailsService, identityLoginPasswordEncoder))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/health",
                        "/error",
                        "/login",
                        "/login/choose",
                        "/login/choose/**",
                        "/external/confirm",
                        "/external/whatsapp",
                        "/external/whatsapp/**",
                        "/external/wechat",
                        "/external/wechat/**",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/forgot-password",
                        "/forgot-password/confirmation",
                        "/reset-password",
                        "/reset-password/confirmation",
                        "/Account/ForgotPassword",
                        "/Account/ForgotPasswordConfirmation",
                        "/Account/ResetPassword",
                        "/Account/ResetPasswordConfirmation",
                        "/.well-known/**",
                        Is4Paths.OAUTH2_JWKS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .securityContext(
            context ->
                context
                    .securityContextRepository(securityContextRepository)
                    .requireExplicitSave(false))
        .requestCache(cache -> cache.requestCache(requestCache))
        .formLogin(
            form ->
                form.loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(
                        loginSuccessHandler(
                            requestCache, securityContextRepository, registeredClientRepository))
                    .failureHandler(
                        (request, response, exception) -> {
                          String location = "/login?error";
                          String returnUrl = request.getParameter("ReturnUrl");
                          if (Is4ReturnUrls.isSafe(returnUrl)) {
                            location =
                                location
                                    + "&ReturnUrl="
                                    + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
                          }
                          response.sendRedirect(location);
                        })
                    .permitAll())
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                    .logoutSuccessHandler(
                        (request, response, authentication) ->
                            response.sendRedirect(
                                StsFormLogout.target(
                                    registeredClientRepository,
                                    request.getParameter("client_id"),
                                    request.getParameter("post_logout_redirect_uri")))));
    if (props.anyExternalLoginConfigured()) {
      ExternalOidcLoginSuccessHandler externalSuccess =
          new ExternalOidcLoginSuccessHandler(
              linker,
              identityUserDetailsService,
              props,
              requestCache,
              securityContextRepository);
      http.oauth2Login(
          oauth ->
              oauth
                  .loginPage("/login")
                  .successHandler(externalSuccess)
                  .failureHandler(
                      (request, response, exception) -> {
                        log.warn(
                            "External OAuth2 login failed: {}",
                            exception == null ? "unknown" : exception.getMessage(),
                            exception);
                        response.sendRedirect("/login?error=external");
                      }));
    }
    return http.build();
  }

  private static AuthenticationManager formLoginAuthenticationManager(
      UserDetailsService userDetailsService, IdentitySpringPasswordEncoder encoder) {
    return new ProviderManager(identityDaoAuthenticationProvider(userDetailsService, encoder));
  }

  private static DaoAuthenticationProvider identityDaoAuthenticationProvider(
      UserDetailsService userDetailsService, IdentitySpringPasswordEncoder encoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(encoder);
    return provider;
  }

  private static Is4LoginSuccessHandler loginSuccessHandler(
      RequestCache requestCache,
      SecurityContextRepository securityContextRepository,
      RegisteredClientRepository registeredClientRepository) {
    Is4LoginSuccessHandler handler =
        new Is4LoginSuccessHandler(securityContextRepository, registeredClientRepository);
    handler.setRequestCache(requestCache);
    return handler;
  }
}
