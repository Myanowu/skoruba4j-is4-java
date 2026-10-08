package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.admin.config.AdminLoginMode;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True when Admin can redirect to STS OIDC ({@code /oauth2/authorization/sts}). */
final class OnAdminStsOidcCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String loginMode = context.getEnvironment().getProperty("idserver.admin.login-mode", "");
    boolean oidcFallback =
        context
            .getEnvironment()
            .getProperty("idserver.admin.oidc-enabled", Boolean.class, Boolean.FALSE);
    return AdminLoginMode.fromConfig(loginMode, oidcFallback).usesOidcClientBeans();
  }
}
