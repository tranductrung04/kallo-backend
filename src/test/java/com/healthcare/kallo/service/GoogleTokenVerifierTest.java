package com.healthcare.kallo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.config.GoogleProperties;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.mockito.Mockito;

class GoogleTokenVerifierTest {
  private JwtEncoder encoder;
  private GoogleTokenVerifier verifier;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
        .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
        .keyID("test-key")
        .build();
    RSAKey verificationKey = signingKey.toPublicJWK();
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(signingKey)));
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(verificationKey.toRSAPublicKey()).build();
    decoder.setJwtValidator(GoogleTokenVerifier.validator(List.of("test-client-id")));
    verifier =
        new GoogleTokenVerifier(
            decoder, new GoogleProperties("test-client-id", "https://example.test/jwks"));
  }

  @Test
  void acceptsGoogleIssuerAndConfiguredAudience() {
    GoogleTokenVerifier.GoogleClaims claims =
        verifier.verify(token("https://accounts.google.com", List.of("test-client-id"), Instant.now().plusSeconds(60)));
    assertThat(claims.email()).isEqualTo("user@example.com");
  }

  @Test
  void rejectsWrongAudience() {
    assertThatThrownBy(
            () -> verifier.verify(token("https://accounts.google.com", List.of("wrong"), Instant.now().plusSeconds(60))))
        .isInstanceOf(BusinessException.class)
    .extracting(e -> ((BusinessException) e).getErrorCode())
    .isEqualTo(com.healthcare.kallo.common.ErrorCode.GOOGLE_TOKEN_INVALID);
  }

  @Test
  void rejectsExpiredToken() {
    assertThatThrownBy(
            () -> verifier.verify(token("accounts.google.com", List.of("test-client-id"), Instant.now().minusSeconds(60))))
        .isInstanceOf(BusinessException.class)
    .extracting(e -> ((BusinessException) e).getErrorCode())
    .isEqualTo(com.healthcare.kallo.common.ErrorCode.GOOGLE_TOKEN_INVALID);
  }

  @Test
  void rejectsWrongIssuer() {
    assertThatThrownBy(
        () ->
            verifier.verify(
                token("https://accounts.example.com", List.of("test-client-id"),
                    Instant.now().plusSeconds(60))))
    .isInstanceOf(BusinessException.class)
    .extracting(e -> ((BusinessException) e).getErrorCode())
    .isEqualTo(com.healthcare.kallo.common.ErrorCode.GOOGLE_TOKEN_INVALID);
  }

  @Test
  void rejectsUnverifiedOrIncompleteIdentityClaims() {
    assertThatThrownBy(
        () ->
            verifier.verify(
                token("https://accounts.google.com", List.of("test-client-id"),
                    Instant.now().plusSeconds(60), null, false)))
    .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
        () ->
            verifier.verify(
                token("https://accounts.google.com", List.of("test-client-id"),
                    Instant.now().plusSeconds(60), null, true)))
    .isInstanceOf(BusinessException.class);
  }

  @Test
  void mapsDecoderInfrastructureFailureToProviderUnavailable() {
    JwtDecoder decoder = Mockito.mock(JwtDecoder.class);
    Mockito.when(decoder.decode("provider-outage"))
    .thenThrow(new org.springframework.security.oauth2.jwt.JwtException("JWKS unavailable"));
    GoogleTokenVerifier outageVerifier =
    new GoogleTokenVerifier(
        decoder, new GoogleProperties("test-client-id", "https://example.test/jwks"));

    assertThatThrownBy(() -> outageVerifier.verify("provider-outage"))
    .isInstanceOf(BusinessException.class)
    .extracting(e -> ((BusinessException) e).getErrorCode())
    .isEqualTo(com.healthcare.kallo.common.ErrorCode.GOOGLE_PROVIDER_UNAVAILABLE);
  }

  private String token(String issuer, List<String> audience, Instant expiresAt) {
    return token(issuer, audience, expiresAt, "user@example.com", true);
  }

  private String token(
      String issuer,
      List<String> audience,
      Instant expiresAt,
      String email,
      boolean emailVerified) {
    JwtClaimsSet.Builder claimsBuilder =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("google-sub")
            .audience(audience)
            .issuedAt(
                expiresAt.isBefore(Instant.now())
                    ? expiresAt.minusSeconds(60)
                    : Instant.now().minusSeconds(1))
            .expiresAt(expiresAt)
            .claim("email_verified", emailVerified)
            .claim("name", "User");
    if (email != null) {
      claimsBuilder.claim("email", email);
    }
    JwtClaimsSet claims = claimsBuilder.build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(), claims))
        .getTokenValue();
  }
}
