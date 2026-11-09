package com.musebeauty.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class SecurityConfig {
  @Bean JwtDecoder jwtDecoder(@Value("${app.supabase.issuer-uri}") String issuer,
      @Value("${app.supabase.jwks-uri}") String jwksUri,
      @Value("${app.supabase.audience}") String expectedAudience) {
    if (issuer.isBlank() || jwksUri.isBlank() || expectedAudience.isBlank()) {
      throw new IllegalStateException("SUPABASE_ISSUER_URI, SUPABASE_JWKS_URI and SUPABASE_AUDIENCE must be configured");
    }
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
      .jwsAlgorithm(SignatureAlgorithm.ES256).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(jwtValidator(issuer, expectedAudience));
    return decoder;
  }

  OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String expectedAudience) {
    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(expectedAudience)
      ? OAuth2TokenValidatorResult.success()
      : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Expected configured audience", null));
    return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience);
  }

  Converter<Jwt, ? extends AbstractAuthenticationToken> supabaseJwtAuthenticationConverter(CustomerProfileService profiles) {
    return jwt -> {
      if (!"authenticated".equals(jwt.getClaimAsString("role")) || Boolean.TRUE.equals(jwt.getClaim("is_anonymous"))) {
        throw new BadCredentialsException("A signed-in Supabase user token is required");
      }
      AppPrincipal principal = profiles.loadOrCreate(jwt);
      return new UsernamePasswordAuthenticationToken(principal, jwt,
        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
    };
  }

  @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String raw) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization","Content-Type"));
    c.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }

  @Bean SecurityFilterChain filterChain(HttpSecurity http, CustomerProfileService profiles) throws Exception {
    return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST,"/api/newsletter").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/catalog/**","/actuator/health").permitAll()
        .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/account/me").hasAnyRole("CUSTOMER","ADMIN")
        .requestMatchers("/api/account/**","/api/basket/**","/api/orders/**").hasRole("CUSTOMER")
        .anyRequest().denyAll())
      .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex)->writeAuthError(res,401,"Unauthorized","A valid Supabase access token is required"))
        .accessDeniedHandler((req,res,ex)->writeAuthError(res,403,"Forbidden","You are not allowed to access this resource")))
      .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(supabaseJwtAuthenticationConverter(profiles))))
      .build();
  }

  private static void writeAuthError(jakarta.servlet.http.HttpServletResponse response,int status,String error,String message) throws IOException {
    response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"timestamp\":\""+Instant.now()+"\",\"status\":"+status+",\"error\":\""+error+"\",\"message\":\""+message+"\"}");
  }
}

record AppPrincipal(long id, java.util.UUID authUserId, String role, String email, boolean active) {}
