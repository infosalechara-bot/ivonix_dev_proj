package com.ivonix.pulse.ontology.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwt, RateLimitingFilter rate) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/actuator/health",
                    "/api/v1/auth/**",
                    "/api/v1/device-gateway/**",
                    "/api/v1/key/activate",
                    "/api/v1/key/capabilities",
                    "/api/v1/key/device/**",
                    "/api/v1/key/oauth/token"
                ).permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(rate, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwt, RateLimitingFilter.class)
            .build();
    }
}
