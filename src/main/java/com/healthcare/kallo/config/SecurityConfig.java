package com.healthcare.kallo.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {
  static final RequestMatcher[] PUBLIC_AUTH_ENDPOINTS = {
    path("/api/auth/register"),
    path("/api/auth/login"),
    path("/api/auth/refresh"),
    path("/api/auth/logout"),
    path("/api/auth/google"),
    path("/api/auth/forgot-password"),
    path("/api/auth/reset-password")
  };

  private static RequestMatcher path(String expected) {
    return request -> {
      String contextPath = request.getContextPath();
      String requestPath = request.getRequestURI().substring(contextPath.length());
      return expected.equals(requestPath);
    };
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties p) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(p.secret().getBytes(StandardCharsets.UTF_8)));
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties p) {
    return NimbusJwtDecoder.withSecretKey(
            new SecretKeySpec(p.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
        .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
        .build();
  }

  @Bean
  BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request -> isPublicAuthEndpoint(request) ? null : delegate.resolve(request);
  }

  private boolean isPublicAuthEndpoint(HttpServletRequest request) {
    for (RequestMatcher matcher : PUBLIC_AUTH_ENDPOINTS) if (matcher.matches(request)) return true;
    return false;
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      com.healthcare.kallo.security.CustomAuthenticationEntryPoint ep,
      com.healthcare.kallo.security.CustomAccessDeniedHandler denied,
      BearerTokenResolver resolver)
      throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(ep).accessDeniedHandler(denied))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(PUBLIC_AUTH_ENDPOINTS)
                    .permitAll()
                    .requestMatchers("/error", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/auth/change-password")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.bearerTokenResolver(resolver)
                    .authenticationEntryPoint(ep)
                    .accessDeniedHandler(denied)
                    .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter c = new JwtAuthenticationConverter();
    c.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString("role");
          if (role == null || role.isBlank()) return java.util.List.of();
          return java.util.List.of(
              new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
        });
    return c;
  }
}
