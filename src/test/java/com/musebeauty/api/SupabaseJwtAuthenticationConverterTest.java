package com.musebeauty.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseJwtAuthenticationConverterTest {
  private final CustomerProfileService profiles = mock(CustomerProfileService.class);
  private final SecurityConfig config = new SecurityConfig();
  private final UUID authUserId = UUID.fromString("f5f714bd-93ad-44cc-97a7-35ad4c249041");

  @Test
  void databaseRoleDeterminesAuthorityEvenIfJwtMetadataClaimsAdmin() {
    Jwt jwt = userToken(Map.of("role", "ADMIN"));
    when(profiles.loadOrCreate(jwt)).thenReturn(new AppPrincipal(12, authUserId, "CUSTOMER", "customer@example.com", true));

    var authentication = config.supabaseJwtAuthenticationConverter(profiles).convert(jwt);

    assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CUSTOMER");
  }

  @Test
  void returnsAdminAuthorityOnlyWhenLoadedApplicationProfileIsAdmin() {
    Jwt jwt = userToken(Map.of());
    when(profiles.loadOrCreate(jwt)).thenReturn(new AppPrincipal(13, authUserId, "ADMIN", "admin@example.com", true));

    var authentication = config.supabaseJwtAuthenticationConverter(profiles).convert(jwt);

    assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
  }

  @Test
  void rejectsAnonymousAndNonAuthenticatedSupabaseTokens() {
    Jwt anonymous = Jwt.withTokenValue("token").header("alg", "RS256").subject(authUserId.toString())
        .claim("role", "authenticated").claim("is_anonymous", true).build();
    Jwt service = Jwt.withTokenValue("token").header("alg", "RS256").subject(authUserId.toString())
        .claim("role", "service_role").build();

    assertThatThrownBy(() -> config.supabaseJwtAuthenticationConverter(profiles).convert(anonymous))
        .isInstanceOf(BadCredentialsException.class);
    assertThatThrownBy(() -> config.supabaseJwtAuthenticationConverter(profiles).convert(service))
        .isInstanceOf(BadCredentialsException.class);
  }

  private Jwt userToken(Map<String, Object> appMetadata) {
    return Jwt.withTokenValue("locally-verified-test-token").header("alg", "RS256")
        .subject(authUserId.toString()).claim("role", "authenticated").claim("is_anonymous", false)
        .claim("app_metadata", appMetadata).claim("email", "customer@example.com").build();
  }
}
