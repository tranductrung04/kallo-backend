package com.healthcare.kallo;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.dto.response.sys.SyncPushResponse;
import com.healthcare.kallo.service.sys.SyncService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class SysRateLimitIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("app.rate-limit.sync-push-per-minute", () -> "30");
  }

  @Autowired MockMvc mockMvc;
  @Autowired JwtEncoder jwtEncoder;
  @MockitoBean SyncService sync;

  @Test
  @Order(1)
  void thirtyFirstAuthenticatedSyncPushIsRejectedAfterSecurityAndNeverReachesController()
      throws Exception {
    UUID userId = UUID.randomUUID();
    when(sync.push(eq(userId), anyList()))
        .thenReturn(new SyncPushResponse(List.of(), Instant.now()));
    String token = token(userId);
    String body = """
        {"operations":[{"clientUuid":"00000000-0000-4000-8000-000000000001",
        "entityType":"WATER_LOG","operation":"UPSERT",
        "updatedAt":"2026-11-13T00:00:00Z","data":{"amountMl":250}}]}
        """;

    for (int request = 1; request <= 30; request++) {
      mockMvc.perform(post("/api/sync/push")
              .header("Authorization", "Bearer " + token)
              .contentType(APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk());
    }

    mockMvc.perform(post("/api/sync/push")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", org.hamcrest.Matchers.notNullValue()));

    verify(sync, times(30)).push(eq(userId), anyList());
  }

  @Test
  @Order(2)
  void rateLimitCounterIsolatedByAuthenticatedUser() throws Exception {
    UUID firstUser = UUID.randomUUID();
    UUID secondUser = UUID.randomUUID();
    when(sync.push(any(), anyList()))
        .thenReturn(new SyncPushResponse(List.of(), Instant.now()));
    String body = pushBody();

    for (int request = 1; request <= 30; request++) {
      mockMvc.perform(post("/api/sync/push")
              .header("Authorization", "Bearer " + token(firstUser))
              .contentType(APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk());
    }
    mockMvc.perform(post("/api/sync/push")
            .header("Authorization", "Bearer " + token(firstUser))
            .contentType(APPLICATION_JSON)
            .content(body))
        .andExpect(status().isTooManyRequests());

    mockMvc.perform(post("/api/sync/push")
            .header("Authorization", "Bearer " + token(secondUser))
            .contentType(APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk());
    verify(sync).push(eq(secondUser), anyList());
  }

  @Test
  @Order(3)
  void stoppedRedisFailsOpenWithoutHoldingRequestForTheClientTimeout() throws Exception {
    UUID userId = UUID.randomUUID();
    when(sync.push(eq(userId), anyList()))
        .thenReturn(new SyncPushResponse(List.of(), Instant.now()));
    REDIS.stop();

    assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
        mockMvc.perform(post("/api/sync/push")
                .header("Authorization", "Bearer " + token(userId))
                .contentType(APPLICATION_JSON)
                .content(pushBody()))
            .andExpect(status().isOk()));
  }

  private String pushBody() {
    return """
        {"operations":[{"clientUuid":"00000000-0000-4000-8000-000000000001",
        "entityType":"WATER_LOG","operation":"UPSERT",
        "updatedAt":"2026-11-13T00:00:00Z","data":{"amountMl":250}}]}
        """;
  }

  private String token(UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(userId.toString())
        .claim("role", "USER")
        .claim("email_verified", true)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .build();
    return jwtEncoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
  }
}
