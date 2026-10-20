package com.healthcare.kallo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.repository.UserRepository;
import com.healthcare.kallo.model.User;
import com.healthcare.kallo.model.RefreshToken;
import com.healthcare.kallo.model.enums.AuthProvider;
import com.healthcare.kallo.model.enums.UserStatus;
import com.healthcare.kallo.repository.RefreshTokenRepository;
import com.healthcare.kallo.service.TokenService;
import com.healthcare.kallo.service.GoogleTokenVerifier;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Autowired UserRepository users;

  @Autowired RefreshTokenRepository refreshTokens;

  @Autowired TokenService tokenService;

  @Autowired PasswordEncoder passwordEncoder;

  @MockitoBean GoogleTokenVerifier googleTokenVerifier;

  private final GoogleTokenVerifier.GoogleClaims googleClaims =
      new GoogleTokenVerifier.GoogleClaims(
          "google-sub-" + System.nanoTime(), "google@example.com", "Google User", null);

  @Test
  void firstGoogleLoginCreatesUserAndSecondLoginReusesIt() throws Exception {
    org.mockito.Mockito.when(googleTokenVerifier.verify("google-token")).thenReturn(googleClaims);

    String first =
        mockMvc
            .perform(
                post("/api/auth/google")
                    .contentType(APPLICATION_JSON)
                    .content("{\"idToken\":\"google-token\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(first).at("/data/isNewUser").asBoolean()).isTrue();

    String second =
        mockMvc
            .perform(
                post("/api/auth/google")
                    .contentType(APPLICATION_JSON)
                    .content("{\"idToken\":\"google-token\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(second).at("/data/isNewUser").asBoolean()).isFalse();
    assertThat(users.findByGoogleSub(googleClaims.subject())).isPresent();
  }

  @Test
  void matchingEmailIsLinkedToGoogleAccount() throws Exception {
    String email = "google-" + System.nanoTime() + "@example.com";
    GoogleTokenVerifier.GoogleClaims claims =
        new GoogleTokenVerifier.GoogleClaims(
            "link-google-sub-" + System.nanoTime(), email, "Local", null);
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\"Password1\",\"displayName\":\"Local\"}"))
        .andExpect(status().isCreated());
    org.mockito.Mockito.when(googleTokenVerifier.verify("link-token")).thenReturn(claims);

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"link-token\"}"))
        .andExpect(status().isOk());

    assertThat(users.findByNormalizedEmail(email).orElseThrow().getGoogleSub())
        .isEqualTo(claims.subject());
  }

  @Test
  void verifiedLocalAccountKeepsPasswordWhenLinked() throws Exception {
    String email = "verified-" + System.nanoTime() + "@example.com";
    User local = new User();
    local.setEmail(email);
    local.setPasswordHash(passwordEncoder.encode("Password1"));
    local.setDisplayName("Verified Local");
    local.setAuthProvider(AuthProvider.LOCAL);
    local.setEmailVerified(true);
    local = users.saveAndFlush(local);
    String subject = "verified-google-sub-" + System.nanoTime();
    GoogleTokenVerifier.GoogleClaims claims =
        new GoogleTokenVerifier.GoogleClaims(subject, email, "Verified", null);
    org.mockito.Mockito.when(googleTokenVerifier.verify("verified-token")).thenReturn(claims);

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"verified-token\"}"))
        .andExpect(status().isOk());

    assertThat(users.findById(local.getId()).orElseThrow().getPasswordHash()).isNotNull();
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password1\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void existingGoogleLinkCannotBeReboundToAnotherGoogleIdentity() throws Exception {
    String email = "conflict-" + System.nanoTime() + "@example.com";
    User linked = new User();
    linked.setEmail(email);
    linked.setGoogleSub("original-google-sub-" + System.nanoTime());
    linked.setDisplayName("Linked");
    linked.setAuthProvider(AuthProvider.GOOGLE);
    linked.setEmailVerified(true);
    users.saveAndFlush(linked);
    GoogleTokenVerifier.GoogleClaims claims =
        new GoogleTokenVerifier.GoogleClaims(
            "replacement-google-sub-" + System.nanoTime(), email, "Replacement", null);
    org.mockito.Mockito.when(googleTokenVerifier.verify("conflict-token")).thenReturn(claims);

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"conflict-token\"}"))
        .andExpect(status().isConflict())
        .andExpect(
            result ->
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .at("/error/code").asText())
                    .isEqualTo("GOOGLE_ACCOUNT_CONFLICT"));
  }

  @Test
  void disabledGoogleUserCannotLoginBySub() throws Exception {
    User disabled = new User();
    disabled.setEmail("disabled-" + System.nanoTime() + "@example.com");
    disabled.setGoogleSub("disabled-google-sub-" + System.nanoTime());
    disabled.setDisplayName("Disabled");
    disabled.setAuthProvider(AuthProvider.GOOGLE);
    disabled.setEmailVerified(true);
    disabled.setStatus(UserStatus.DISABLED);
    users.saveAndFlush(disabled);
    GoogleTokenVerifier.GoogleClaims claims =
        new GoogleTokenVerifier.GoogleClaims(
            disabled.getGoogleSub(), disabled.getEmail(), "Disabled", null);
    org.mockito.Mockito.when(googleTokenVerifier.verify("disabled-token")).thenReturn(claims);

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"disabled-token\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unverifiedLocalAccountLosesPasswordAndRefreshTokensWhenLinked() throws Exception {
    String email = "takeover-" + System.nanoTime() + "@example.com";
    String registered =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"PLACEHOLDER","password":"Password1","displayName":"Local"}
                        """.replace("PLACEHOLDER", email)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String refresh = objectMapper.readTree(registered).at("/data/refreshToken").asText();
    User local = users.findByNormalizedEmail(email).orElseThrow();
    GoogleTokenVerifier.GoogleClaims claims =
        new GoogleTokenVerifier.GoogleClaims(
            "takeover-google-sub-" + System.nanoTime(), email, "Takeover", null);
    org.mockito.Mockito.when(googleTokenVerifier.verify("takeover-token")).thenReturn(claims);

    mockMvc
        .perform(
            post("/api/auth/google")
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"takeover-token\"}"))
        .andExpect(status().isOk());

    assertThat(users.findById(local.getId()).orElseThrow().getPasswordHash()).isNull();
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\"Password1\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registerThenLoginAndProtectApi() throws Exception {
    String registerBody =
        """
        {"email":"user@example.com","password":"Password1","displayName":"User"}
        """;

    String registerResponse =
        mockMvc
            .perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(registerBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode registerJson = objectMapper.readTree(registerResponse);
    assertThat(registerJson.path("success").asBoolean()).isTrue();
    assertThat(registerJson.at("/data/user/email").asText()).isEqualTo("user@example.com");
    assertThat(users.findByNormalizedEmail("user@example.com")).isPresent();
    assertThat(users.findByNormalizedEmail("user@example.com").orElseThrow().getCreatedAt())
        .isNotNull();

    long usersBeforeDuplicate = users.count();
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"email":"USER@example.com","password":"Password1","displayName":"Duplicate"}
                    """))
        .andExpect(status().isConflict());
    assertThat(users.count()).isEqualTo(usersBeforeDuplicate);

    String loginResponse =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"user@example.com","password":"Password1"}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = objectMapper.readTree(loginResponse).at("/data/accessToken").asText();

    mockMvc
        .perform(get("/api/test/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .at("/error/code")
                            .asText())
                    .isEqualTo("UNAUTHORIZED"));
    mockMvc
        .perform(
            post("/api/auth/change-password")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Password1","newPassword":"NewPassword1"}
                    """))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/test/protected").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Password1","newPassword":"NewPassword1"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void invalidCredentialsAndValidationBoundariesAreRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"email":"boundary@example.com","password":"Pass1234","displayName":"Boundary"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"email":"boundary@example.com","password":"Wrong123"}
                    """))
        .andExpect(status().isUnauthorized());

    String oversizedPassword = "Aa1" + "á".repeat(35);
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"email\":\"bytes@example.com\",\"password\":\""
                        + oversizedPassword
                        + "\",\"displayName\":\"Bytes\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .at("/error/details/password")
                            .isMissingNode())
                    .isFalse());
  }

  @Test
  void refreshRotatesTokenAndLogoutRevokesIt() throws Exception {
    String login =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"rotation@example.com","password":"Password1","displayName":"Rotation"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String oldRefresh = objectMapper.readTree(login).at("/data/refreshToken").asText();

    String rotated =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefresh = objectMapper.readTree(rotated).at("/data/refreshToken").asText();

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .at("/error/code").asText())
                    .isEqualTo("REFRESH_TOKEN_INVALID"));

    String refreshedAgain =
        mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    String latestRefresh = objectMapper.readTree(refreshedAgain).at("/data/refreshToken").asText();

    mockMvc
        .perform(
            post("/api/auth/logout")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + latestRefresh + "\",\"allDevices\":false}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + latestRefresh + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changePasswordValidatesCredentialsAndRevokesOldSession() throws Exception {
    String registered =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"password-phase3@example.com","password":"Password1","displayName":"Password"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode registeredJson = objectMapper.readTree(registered);
    String access = registeredJson.at("/data/accessToken").asText();
    String refresh = registeredJson.at("/data/refreshToken").asText();

    mockMvc
        .perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + access)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Wrong123","newPassword":"NewPassword1"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .at("/error/code").asText())
                    .isEqualTo("INVALID_CURRENT_PASSWORD"));

    mockMvc
        .perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + access)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Password1","newPassword":"Password1"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .at("/error/code").asText())
                    .isEqualTo("PASSWORD_UNCHANGED"));

    String changed =
        mockMvc
            .perform(
                post("/api/auth/change-password")
                    .header("Authorization", "Bearer " + access)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"currentPassword":"Password1","newPassword":"NewPassword1"}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefresh = objectMapper.readTree(changed).at("/data/refreshToken").asText();

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"email":"password-phase3@example.com","password":"NewPassword1"}
                    """))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void allDevicesAndDisabledUsersCannotRefresh() throws Exception {
    String first =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"all-devices@example.com","password":"Password1","displayName":"Devices"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstRefresh = objectMapper.readTree(first).at("/data/refreshToken").asText();
    String second =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"all-devices@example.com","password":"Password1"}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondRefresh = objectMapper.readTree(second).at("/data/refreshToken").asText();

    mockMvc
        .perform(
            post("/api/auth/logout")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + firstRefresh + "\",\"allDevices\":true}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
        .andExpect(status().isUnauthorized());

    User user = users.findByNormalizedEmail("all-devices@example.com").orElseThrow();
    user.setStatus(UserStatus.DISABLED);
    users.saveAndFlush(user);
    String disabledRefresh = tokenService.issue(user).refreshToken();
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + disabledRefresh + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredRefreshAndGoogleWithoutPasswordAreRejected() throws Exception {
    String registered =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"expired-phase3@example.com","password":"Password1","displayName":"Expired"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String refresh = objectMapper.readTree(registered).at("/data/refreshToken").asText();
    User local = users.findByNormalizedEmail("expired-phase3@example.com").orElseThrow();
    var expiredToken = refreshTokens.findFirstByUserOrderByCreatedAtDesc(local).orElseThrow();
    expiredToken.setExpiresAt(Instant.now().minusSeconds(1));
    refreshTokens.saveAndFlush(expiredToken);
    refreshTokens.flush();
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isUnauthorized());

    User google = new User();
    google.setEmail("google-phase3@example.com");
    google.setDisplayName("Google");
    google.setGoogleSub("google-phase3-sub");
    google.setAuthProvider(AuthProvider.GOOGLE);
    google.setEmailVerified(true);
    google = users.saveAndFlush(google);
    String googleAccess = tokenService.issue(google).accessToken();
    mockMvc
        .perform(
            post("/api/auth/change-password")
                .header("Authorization", "Bearer " + googleAccess)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"Password1","newPassword":"NewPassword1"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .at("/error/code").asText())
                    .isEqualTo("PASSWORD_NOT_SET"));
  }

  @Test
  void concurrentRefreshRequestsAcceptOnlyOneUseOfTheToken() throws Exception {
    String registered =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"concurrent-phase3@example.com","password":"Password1","displayName":"Concurrent"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String refresh = objectMapper.readTree(registered).at("/data/refreshToken").asText();
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> refreshStatus(refresh, start));
      var second = executor.submit(() -> refreshStatus(refresh, start));
      start.countDown();
      assertThat(java.util.List.of(first.get(), second.get()))
          .containsExactlyInAnyOrder(200, 401);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void refreshReuseAfterGraceRevokesFamily() throws Exception {
    String registered =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"email":"grace-window@example.com","password":"Password1","displayName":"Grace"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String oldRefresh = objectMapper.readTree(registered).at("/data/refreshToken").asText();
    String rotated =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newRefresh = objectMapper.readTree(rotated).at("/data/refreshToken").asText();
    User user = users.findByNormalizedEmail("grace-window@example.com").orElseThrow();
    RefreshToken revoked =
        refreshTokens.findFirstByUserAndRevokedAtIsNotNullOrderByCreatedAtDesc(user).orElseThrow();
    revoked.setRevokedAt(Instant.now().minusSeconds(30));
    refreshTokens.saveAndFlush(revoked);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithoutTokenIsStillNoContent() throws Exception {
    mockMvc.perform(post("/api/auth/logout").contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isNoContent());
  }

  private int refreshStatus(String refresh, CountDownLatch start) throws Exception {
    start.await();
    return mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void publicLoginIgnoresInvalidBearerHeader() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .header("Authorization", "Bearer invalid-signature")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"email":"missing@example.com","password":"Wrong123"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .at("/error/code")
                            .asText())
                    .isEqualTo("INVALID_CREDENTIALS"));
  }
}
