package com.ivonix.pulse.ontology.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtAuthenticationFilter jwt,RateLimitingFilter rate,AuditLoggingFilter audit)throws Exception{
    return http.csrf(csrf->csrf.disable())
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
        .httpStrictTransportSecurity(hsts->hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
      // Human APIs require a verified JWT. Device ingress is separately authenticated by
      // DeviceGatewayService using the device credential bound to key_devices.organization_id.
      // It must not be forced through the human-JWT filter, otherwise real device credentials
      // are rejected before the device identity/tenant check can run.
      .authorizeHttpRequests(a->a
        .requestMatchers("/actuator/health","/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/device-gateway/**").permitAll()
        .anyRequest().authenticated())
      .addFilterBefore(rate,UsernamePasswordAuthenticationFilter.class)
      .addFilterAfter(jwt,RateLimitingFilter.class)
      .addFilterAfter(audit,JwtAuthenticationFilter.class).build();
  }
}
