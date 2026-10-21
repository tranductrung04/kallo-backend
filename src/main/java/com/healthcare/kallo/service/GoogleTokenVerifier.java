package com.healthcare.kallo.service;

import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.config.GoogleProperties;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class GoogleTokenVerifier {
  private static final String GOOGLE_ISSUER = "https://accounts.google.com";
  private final JwtDecoder decoder;
  private final GoogleProperties properties;
  private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

  public GoogleTokenVerifier(
      @Qualifier("googleJwtDecoder") JwtDecoder decoder, GoogleProperties properties) {
    this.properties = properties;
    this.decoder = decoder;
  }

  public static OAuth2TokenValidator<Jwt> validator(List<String> clientIds) {
    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(), new IssuerValidator(), new AudienceValidator(clientIds));
  }

  public GoogleClaims verify(String idToken) {
    try {
      if (properties.clientIdList().isEmpty()) {
        throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
      }
      Jwt jwt = decoder.decode(idToken);
      String subject = jwt.getSubject();
      String email = jwt.getClaimAsString("email");
      Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
      if (subject == null
          || email == null
          || email.isBlank()
          || !Boolean.TRUE.equals(emailVerified)) {
        throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
      }
      return new GoogleClaims(
          subject,
          email,
          jwt.getClaimAsString("name"),
          jwt.getClaimAsString("picture"),
          jwt.getIssuedAt());
    } catch (BusinessException e) {
      throw e;
    } catch (BadJwtException e) {
      log.warn("Google token verification failed: {}", e.getMessage());
      throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
    } catch (JwtException e) {
      log.warn("Google token provider unavailable: {}", e.getMessage());
      throw new BusinessException(ErrorCode.GOOGLE_PROVIDER_UNAVAILABLE);
    } catch (IllegalArgumentException e) {
      log.warn("Google token verification failed: {}", e.getMessage());
      throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
    }
  }

  public GoogleClaims verifyFresh(String idToken) {
    GoogleClaims claims = verify(idToken);
    if (claims.issuedAt() == null
        || claims.issuedAt().isBefore(Instant.now().minus(Duration.ofMinutes(5)))
        || claims.issuedAt().isAfter(Instant.now().plusSeconds(30))) {
      throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID);
    }
    return claims;
  }

  public record GoogleClaims(
      String subject, String email, String name, String picture, Instant issuedAt) {
    public GoogleClaims(String subject, String email, String name, String picture) {
      this(subject, email, name, picture, null);
    }
  }

  public static final class IssuerValidator implements OAuth2TokenValidator<Jwt> {
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
      String issuer = token.getIssuer() == null ? null : token.getIssuer().toString();
      return "accounts.google.com".equals(issuer) || GOOGLE_ISSUER.equals(issuer)
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "Invalid Google token issuer", null));
    }
  }

  public static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final List<String> clientIds;

    public AudienceValidator(List<String> clientIds) {
      this.clientIds = clientIds;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
      return token.getAudience() != null && token.getAudience().stream().anyMatch(clientIds::contains)
          ? OAuth2TokenValidatorResult.success()
          : OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "Invalid Google token audience", null));
    }
  }
}
