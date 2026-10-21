package com.healthcare.kallo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.repository.UserRepository;
import com.healthcare.kallo.model.User;
import com.healthcare.kallo.model.RefreshToken;
import com.healthcare.kallo.model.enums.AuthProvider;
import com.healthcare.kallo.model.enums.UserStatus;
import com.healthcare.kallo.repository.RefreshTokenRepository;
import com.healthcare.kallo.repository.EmailVerificationTokenRepository;
import com.healthcare.kallo.repository.PasswordResetTokenRepository;
import com.healthcare.kallo.service.TokenService;
import com.healthcare.kallo.service.GoogleTokenVerifier;
import com.healthcare.kallo.service.EmailService;
import com.healthcare.kallo.service.StorageService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.mockito.ArgumentCaptor;
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

  @Autowired EmailVerificationTokenRepository verificationTokens;

  @Autowired PasswordResetTokenRepository passwordResetTokens;

  @Autowired TokenService tokenService;

  @Autowired PasswordEncoder passwordEncoder;

  @Autowired JdbcTemplate jdbcTemplate;

  @MockitoBean GoogleTokenVerifier googleTokenVerifier;

  @MockitoBean EmailService emailService;

  @MockitoBean StorageService storageService;

  @Test
  void updateProfilePersistsThroughHttpAndPostgres() throws Exception {
    User user = new User();
    user.setEmail("phase6-" + System.nanoTime() + "@example.com");
    user.setPasswordHash(passwordEncoder.encode("Password1"));
    user.setDisplayName("Before");
    user.setTimezone("Asia/Ho_Chi_Minh");
    user.setAuthProvider(AuthProvider.LOCAL);
    user.setEmailVerified(true);
    user = users.saveAndFlush(user);
    Instant beforeUpdate = user.getUpdatedAt();
    String accessToken = tokenService.issue(user).accessToken();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"After\",\"timezone\":\"Europe/Paris\"}"))
        .andExpect(status().isOk());

    User updated = users.findById(user.getId()).orElseThrow();
    assertThat(updated.getDisplayName()).isEqualTo("After");
    assertThat(updated.getTimezone()).isEqualTo("Europe/Paris");
    assertThat(updated.getUpdatedAt()).isAfter(beforeUpdate);
  }

  @Test
  void avatarUploadPersistsThroughHttpAndReturnsPresignedUrl() throws Exception {
    User user = phase6User("avatar");
    String accessToken = tokenService.issue(user).accessToken();
    when(storageService.presignedUrl(any(), any())).thenReturn("http://localhost:9000/avatar");

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/api/users/me/avatar")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "file", "avatar.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
            .at("/data/avatarUrl").asText()).isEqualTo("http://localhost:9000/avatar"));

    User updated = users.findById(user.getId()).orElseThrow();
    assertThat(updated.getAvatarUrl()).startsWith("avatars/" + user.getId() + "/");
    assertThat(updated.getAvatarUrl()).endsWith(".jpg");
    verify(storageService).upload(any(), any(), org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("image/jpeg"));
  }

  @Test
  void deleteAccountRemovesTokensAndRejectsLoginThroughHttp() throws Exception {
    User user = phase6User("delete");
    String accessToken = tokenService.issue(user).accessToken();
    String refreshToken = tokenService.issue(user).refreshToken();
    user.setAvatarUrl("avatars/" + user.getId() + "/old.jpg");
    users.saveAndFlush(user);
    reset(storageService);

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("{\"password\":\"Password1\"}"))
        .andExpect(status().isNoContent());

    verify(storageService).deletePrefix("avatars/" + user.getId() + "/");
    assertThat(users.findById(user.getId())).isEmpty();
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from refresh_tokens where user_id = ?", Long.class, user.getId()))
        .isZero();
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from password_reset_tokens where user_id = ?", Long.class, user.getId()))
        .isZero();
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from email_verification_tokens where user_id = ?", Long.class, user.getId()))
        .isZero();
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Password1\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteAccountRejectsWrongPasswordAndMissingConfirmation() throws Exception {
    User user = phase6User("delete-invalid");
    String accessToken = tokenService.issue(user).accessToken();

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("{\"password\":\"WrongPassword1\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
            .at("/error/code").asText()).isEqualTo("INVALID_DELETE_CONFIRMATION"));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
            .at("/error/code").asText()).isEqualTo("INVALID_DELETE_CONFIRMATION"));
  }

  @Test
  void googleAccountDeletionAcceptsFreshTokenThroughHttp() throws Exception {
    User user = phase6User("delete-google");
    user.setPasswordHash(null);
    user.setGoogleSub("delete-google-sub-" + System.nanoTime());
    user.setAuthProvider(AuthProvider.GOOGLE);
    users.saveAndFlush(user);
    String accessToken = tokenService.issue(user).accessToken();
    when(googleTokenVerifier.verifyFresh("fresh-delete-token"))
        .thenReturn(new GoogleTokenVerifier.GoogleClaims(user.getGoogleSub(), user.getEmail(), user.getDisplayName(), null));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content("{\"idToken\":\"fresh-delete-token\"}"))
        .andExpect(status().isNoContent());

    verify(googleTokenVerifier).verifyFresh("fresh-delete-token");
    assertThat(users.findById(user.getId())).isEmpty();
  }

  private User phase6User(String suffix) {
    User user = new User();
    user.setEmail("phase6-" + suffix + "-" + System.nanoTime() + "@example.com");
    user.setPasswordHash(passwordEncoder.encode("Password1"));
    user.setDisplayName("Phase 6");
    user.setTimezone("Asia/Ho_Chi_Minh");
    user.setAuthProvider(AuthProvider.LOCAL);
    user.setEmailVerified(true);
    return users.saveAndFlush(user);
  }

  @Test
  void unauthenticatedGetMeIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void everyForeignKeyReferencingUsersCascadesOnDelete() {
    var constraints =
        jdbcTemplate.queryForList(
            """
            select rc.constraint_name, rc.delete_rule
            from information_schema.referential_constraints rc
            join information_schema.table_constraints tc
              on tc.constraint_name = rc.constraint_name
             and tc.constraint_schema = rc.constraint_schema
            join information_schema.constraint_column_usage ccu
              on ccu.constraint_name = tc.constraint_name
             and ccu.constraint_schema = tc.constraint_schema
            where tc.constraint_type = 'FOREIGN KEY'
              and ccu.table_name = 'users'
              and tc.constraint_schema = 'public'
              and tc.table_name <> 'users'
            """);
    assertThat(constraints).isNotEmpty();
    assertThat(constraints).allSatisfy(row -> assertThat(row.get("delete_rule")).isEqualTo("CASCADE"));
  }

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
        .andExpect(status().isForbidden())
        .andExpect(
            result ->
                assertThat(
                        objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .at("/error/code")
                            .asText())
                    .isEqualTo("EMAIL_NOT_VERIFIED"));
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
  void verificationAttemptsPersistThroughTransactionalFailuresAndLockCode() throws Exception {
    String email = "verify-rollback-" + System.nanoTime() + "@example.com";
    String registration =
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(
                "{\"email\":\"" + email + "\",\"password\":\"Password1\",\"displayName\":\"Verify\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String access = objectMapper.readTree(registration).at("/data/accessToken").asText();
    assertThat(objectMapper.readTree(registration).at("/data/user/emailVerified").asBoolean())
        .isFalse();
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(emailService).sendVerificationCode(any(User.class), code.capture());

    for (int i = 0; i < 5; i++) {
      mockMvc.perform(post("/api/auth/verify-email").header("Authorization", "Bearer " + access)
              .contentType(APPLICATION_JSON).content("{\"code\":\"000000\"}"))
          .andExpect(status().isBadRequest());
    }

    User user = users.findByNormalizedEmail(email).orElseThrow();
    var token = verificationTokens.findAll().stream()
        .filter(t -> t.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
    assertThat(token.getAttempts()).isEqualTo(5);
    assertThat(token.getUsedAt()).isNotNull();
    mockMvc.perform(post("/api/auth/verify-email").header("Authorization", "Bearer " + access)
            .contentType(APPLICATION_JSON).content("{\"code\":\"" + code.getValue() + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verifiedAccessTokenWorksWhilePreVerificationTokenRemainsBlocked() throws Exception {
    String email = "verify-success-" + System.nanoTime() + "@example.com";
    String registration =
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(
                "{\"email\":\"" + email + "\",\"password\":\"Password1\",\"displayName\":\"Verify\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String oldAccess = objectMapper.readTree(registration).at("/data/accessToken").asText();
    String refresh = objectMapper.readTree(registration).at("/data/refreshToken").asText();
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(emailService).sendVerificationCode(any(User.class), code.capture());

    String verification =
        mockMvc.perform(post("/api/auth/verify-email").header("Authorization", "Bearer " + oldAccess)
                .contentType(APPLICATION_JSON).content("{\"code\":\"" + code.getValue() + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String newAccess = objectMapper.readTree(verification).at("/data/accessToken").asText();
    assertThat(objectMapper.readTree(verification).at("/data/refreshToken").isNull()).isTrue();
    assertThat(objectMapper.readTree(verification).at("/data/user/emailVerified").asBoolean())
        .isTrue();

    mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer " + oldAccess))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer " + newAccess))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void resetAttemptsPersistThroughTransactionalFailuresAndLockCode() throws Exception {
    String email = "reset-rollback-" + System.nanoTime() + "@example.com";
    mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(
            "{\"email\":\"" + email + "\",\"password\":\"Password1\",\"displayName\":\"Reset\"}"))
        .andExpect(status().isCreated());
    org.mockito.Mockito.clearInvocations(emailService);
    mockMvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(emailService).sendPasswordResetCode(any(User.class), code.capture());

    for (int i = 0; i < 5; i++) {
      mockMvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON).content(
              "{\"email\":\"" + email + "\",\"code\":\"000000\",\"newPassword\":\"NewPassword1\"}"))
          .andExpect(status().isBadRequest());
    }
    User user = users.findByNormalizedEmail(email).orElseThrow();
    var token = passwordResetTokens.findAll().stream()
        .filter(t -> t.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
    assertThat(token.getAttempts()).isEqualTo(5);
    assertThat(token.getUsedAt()).isNotNull();
    mockMvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON).content(
            "{\"email\":\"" + email + "\",\"code\":\"" + code.getValue()
                + "\",\"newPassword\":\"NewPassword1\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resetPasswordSuccessChangesPasswordVerifiesEmailAndRevokesRefreshToken() throws Exception {
    String email = "reset-success-" + System.nanoTime() + "@example.com";
    String registration = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Password1\",\"displayName\":\"Reset\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String oldRefresh = objectMapper.readTree(registration).at("/data/refreshToken").asText();
    org.mockito.Mockito.clearInvocations(emailService);
    mockMvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(emailService).sendPasswordResetCode(any(User.class), code.capture());

    mockMvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"code\":\"" + code.getValue()
                + "\",\"newPassword\":\"NewPassword1\"}"))
        .andExpect(status().isNoContent());

    assertThat(users.findByNormalizedEmail(email).orElseThrow().isEmailVerified()).isTrue();
    mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Password1\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"NewPassword1\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void forgotPasswordDoesNotRevealUnknownEmailOrSendDuplicateWithinInterval() throws Exception {
    String unknown = "unknown-" + System.nanoTime() + "@example.com";
    mockMvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + unknown + "\"}")).andExpect(status().isOk());
    verify(emailService, never()).sendPasswordResetCode(any(User.class), any());

    String email = "forgot-limit-" + System.nanoTime() + "@example.com";
    mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Password1\",\"displayName\":\"Forgot\"}"))
        .andExpect(status().isCreated());
    clearInvocations(emailService);
    mockMvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
    mockMvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
    verify(emailService, times(1)).sendPasswordResetCode(any(User.class), any());
  }

  @Test
  void resendVerificationIsRateLimitedAndVerifiedUsersCannotResend() throws Exception {
    String email = "resend-limit-" + System.nanoTime() + "@example.com";
    String registration = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Password1\",\"displayName\":\"Resend\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String access = objectMapper.readTree(registration).at("/data/accessToken").asText();
    mockMvc.perform(post("/api/auth/resend-verification").header("Authorization", "Bearer " + access))
        .andExpect(status().isTooManyRequests());

    User user = users.findByNormalizedEmail(email).orElseThrow();
    user.setEmailVerified(true);
    users.saveAndFlush(user);
    mockMvc.perform(post("/api/auth/resend-verification").header("Authorization", "Bearer " + access))
        .andExpect(status().isConflict());
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
