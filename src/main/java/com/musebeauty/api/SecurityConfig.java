package com.musebeauty.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String raw) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization","Content-Type"));
    c.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", c); return source;
  }
  @Bean SecurityFilterChain filterChain(HttpSecurity http, JdbcTemplate jdbc) throws Exception {
    return http.csrf(csrf -> csrf.disable()).cors(cors -> {}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login","/api/auth/logout","/api/newsletter").permitAll()
       .requestMatchers(HttpMethod.GET,"/api/catalog/**","/actuator/health").permitAll()
       .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll().requestMatchers("/api/admin/**").hasRole("ADMIN")
       .requestMatchers("/api/account/**","/api/basket/**","/api/orders/**").hasRole("CUSTOMER").anyRequest().denyAll())
      .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex)->writeAuthError(res,401,"Unauthorized","Authentication required"))
        .accessDeniedHandler((req,res,ex)->writeAuthError(res,403,"Forbidden","You are not allowed to access this resource")))
      .addFilterBefore(new SessionTokenFilter(jdbc), UsernamePasswordAuthenticationFilter.class).build();
  }
  private static void writeAuthError(HttpServletResponse response,int status,String error,String message) throws IOException {
    response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"status\":"+status+",\"error\":\""+error+"\",\"message\":\""+message+"\"}");
  }
}

record AppPrincipal(long id, String role, String email) {}

class SessionTokenFilter extends OncePerRequestFilter {
  private final JdbcTemplate jdbc;
  SessionTokenFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  static String hash(String token) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7).trim();
      if (token.length() > 20 && token.length() < 200) {
        var rows = jdbc.query("select c.id,c.role,c.email from sessions s join customers c on c.id=s.customer_id where s.token_hash=? and s.expires_at>? and c.active=true", (rs,n) -> new AppPrincipal(rs.getLong(1),rs.getString(2),rs.getString(3)), hash(token), Timestamp.from(Instant.now()));
        if (!rows.isEmpty()) {
          AppPrincipal p = rows.get(0);
          var auth = new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role())));
          org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }
    chain.doFilter(req,res);
  }
}
