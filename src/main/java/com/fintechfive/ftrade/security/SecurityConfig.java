package com.fintechfive.ftrade.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Value("${jwt.shared-secret}")
  private String sharedSecret;

  @Bean
  public JwtDecoder jwtDecoder() {
    SecretKey key = new SecretKeySpec(sharedSecret.getBytes(UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).build();
  }

  // auth-service puts the user's roles in a "roles" claim (e.g. ["CLIENT"]). Spring only reads "scope" by default,
  // so without this every user has no roles and hasRole(...) always fails with 403. CLIENT -> ROLE_CLIENT
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return converter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/public").permitAll()
        // the Docker and Jenkins health checks call this without a token
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/auth/register").permitAll()
        .requestMatchers("/auth/login").permitAll()
        // only client logins; ADMIN and ANALYST use the reporting dashboard instead
        .requestMatchers("/v1/clients/**").hasRole("CLIENT")
        .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) -> res.sendError(401))
            .accessDeniedHandler((req, res, e) -> res.sendError(403)))
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
    return http.build();
  }
}
