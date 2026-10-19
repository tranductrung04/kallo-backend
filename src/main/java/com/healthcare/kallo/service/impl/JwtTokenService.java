package com.healthcare.kallo.service.impl;

import com.healthcare.kallo.config.JwtProperties;
import com.healthcare.kallo.common.BusinessException;
import com.healthcare.kallo.common.ErrorCode;
import com.healthcare.kallo.model.RefreshToken;
import com.healthcare.kallo.model.User;
import com.healthcare.kallo.model.enums.UserStatus;
import com.healthcare.kallo.repository.RefreshTokenRepository;
import com.healthcare.kallo.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JwtTokenService implements TokenService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final JwtEncoder encoder;
  private final JwtProperties props;
  private final RefreshTokenRepository repo;

  public JwtTokenService(JwtEncoder encoder, JwtProperties props, RefreshTokenRepository repo) {
    this.encoder = encoder;
    this.props = props;
    this.repo = repo;
  }

  public TokenPair issue(User u) {
    return issue(u, UUID.randomUUID());
  }

  private TokenPair issue(User u, UUID familyId) {
    String access = accessToken(u);
    String refresh = createRefreshToken(u, familyId);
    return new TokenPair(u.getId(), access, refresh);
  }

  private String accessToken(User u) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(u.getId().toString())
            .claim("role", u.getRole().name())
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusMillis(props.accessExpiration()))
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Transactional(noRollbackFor = BusinessException.class)
  public TokenPair rotate(String rawToken) {
    RefreshToken current =
        repo.findByTokenHash(hash(rawToken)).orElseThrow(
            () -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
    Instant now = Instant.now();
    if (current.getRevokedAt() != null) {
      if (Duration.between(current.getRevokedAt(), now).toMillis() > props.refreshReuseGrace()) {
        repo.revokeByFamily(current.getFamilyId(), now);
      }
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    if (!current.getExpiresAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    if (current.getUser().getStatus() == UserStatus.DISABLED) {
      repo.revokeByUser(current.getUser().getId(), now);
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
    }
    current.setRevokedAt(now);
    repo.save(current);
    return issue(current.getUser(), current.getFamilyId());
  }

  @Transactional
  public void revoke(String rawToken) {
    repo.findByTokenHash(hash(rawToken)).ifPresent(token -> {
      if (token.getRevokedAt() == null) {
        token.setRevokedAt(Instant.now());
        repo.save(token);
      }
    });
  }

  @Transactional
  public void revokeAll(User user) {
    repo.revokeByUser(user.getId(), Instant.now());
  }

  @Transactional
  public void revokeAllByToken(String rawToken) {
    repo.findByTokenHash(hash(rawToken)).ifPresent(token -> revokeAll(token.getUser()));
  }

  private String createRefreshToken(User u, UUID familyId) {
    byte[] b = new byte[32];
    SECURE_RANDOM.nextBytes(b);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    RefreshToken r = new RefreshToken();
    r.setUser(u);
    r.setFamilyId(familyId);
    r.setTokenHash(hash(token));
    r.setExpiresAt(Instant.now().plusMillis(props.refreshExpiration()));
    r.setCreatedAt(Instant.now());
    repo.save(r);
    return token;
  }

  public long accessTokenLifetimeSeconds() {
    return props.accessExpiration() / 1000;
  }

  private String hash(String s) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(h);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
