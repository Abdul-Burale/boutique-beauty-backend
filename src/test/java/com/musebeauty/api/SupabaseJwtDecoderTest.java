package com.musebeauty.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class SupabaseJwtDecoderTest {
  private static final String ISSUER = "https://unit-test.supabase.co/auth/v1";
  private RSAKey signingKey;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    NimbusJwtDecoder configuredDecoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
        .signatureAlgorithm(SignatureAlgorithm.RS256).build();
    configuredDecoder.setJwtValidator(new SecurityConfig().jwtValidator(ISSUER, "authenticated"));
    decoder = configuredDecoder;
  }

  @Test
  void acceptsLocallySignedTokenWithExpectedIssuerAndAudience() throws Exception {
    Jwt jwt = decoder.decode(token(Instant.now().minusSeconds(5), Instant.now().plusSeconds(60), ISSUER, "authenticated"));
    assertThat(jwt.getSubject()).isEqualTo("f5f714bd-93ad-44cc-97a7-35ad4c249041");
  }

  @Test
  void rejectsMalformedAndTamperedTokens() throws Exception {
    assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(JwtException.class);
    String valid = token(Instant.now().minusSeconds(5), Instant.now().plusSeconds(60), ISSUER, "authenticated");
    String[] pieces = valid.split("\\.");
    String signature = pieces[2];
    String changedSignature = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
    assertThatThrownBy(() -> decoder.decode(pieces[0] + "." + pieces[1] + "." + changedSignature))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsExpiredToken() throws Exception {
    String expired = token(Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), ISSUER, "authenticated");
    assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsUnexpectedIssuerOrAudience() throws Exception {
    String wrongIssuer = token(Instant.now().minusSeconds(5), Instant.now().plusSeconds(60), "https://other.example/auth/v1", "authenticated");
    String wrongAudience = token(Instant.now().minusSeconds(5), Instant.now().plusSeconds(60), ISSUER, "other");
    assertThatThrownBy(() -> decoder.decode(wrongIssuer)).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder.decode(wrongAudience)).isInstanceOf(JwtException.class);
  }

  private String token(Instant issuedAt, Instant expiresAt, String issuer, String audience) throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
        .subject(UUID.fromString("f5f714bd-93ad-44cc-97a7-35ad4c249041").toString())
        .issueTime(Date.from(issuedAt)).expirationTime(Date.from(expiresAt)).build();
    SignedJWT signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
    signed.sign(new RSASSASigner(signingKey));
    return signed.serialize();
  }
}
