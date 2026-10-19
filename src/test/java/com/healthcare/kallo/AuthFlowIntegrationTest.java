package com.healthcare.kallo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
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
        .andExpect(status().isNotFound());
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
