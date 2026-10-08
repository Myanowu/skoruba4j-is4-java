package com.myano.skoruba4j.admin.security;

import com.myano.skoruba4j.admin.config.AdminLoginMode;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True when Admin form login checks {@code Users.PasswordHash} locally. */
final class OnAdminLocalPasswordCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String loginMode = context.getEnvironment().getProperty("idserver.admin.login-mode", "");
    boolean oidcFallback =
        context
            .getEnvironment()
            .getProperty("idserver.admin.oidc-enabled", Boolean.class, Boolean.FALSE);
    AdminLoginMode mode = AdminLoginMode.fromConfig(loginMode, oidcFallback);
    return mode == AdminLoginMode.LOCAL || mode == AdminLoginMode.BOTH;
  }
}
