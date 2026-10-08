package com.myano.skoruba4j.admin.config;

import com.myano.skoruba4j.admin.web.AdminAuditLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminWebMvcConfiguration implements WebMvcConfigurer {
  private final AdminAuditLogInterceptor auditLogInterceptor;

  public AdminWebMvcConfiguration(AdminAuditLogInterceptor auditLogInterceptor) {
    this.auditLogInterceptor = auditLogInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(auditLogInterceptor).addPathPatterns("/admin/**");
  }
}
