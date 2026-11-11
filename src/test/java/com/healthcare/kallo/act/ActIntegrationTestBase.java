package com.healthcare.kallo.act;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.model.auth.enums.AuthProvider;
import com.healthcare.kallo.repository.auth.UserRepository;
import com.healthcare.kallo.service.auth.EmailService;
import com.healthcare.kallo.service.auth.GoogleTokenVerifier;
import com.healthcare.kallo.service.auth.StorageService;
import com.healthcare.kallo.service.auth.TokenService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class ActIntegrationTestBase {
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected UserRepository users;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected TokenService tokenService;

  @MockitoBean protected StorageService storageService;
  @MockitoBean protected EmailService emailService;
  @MockitoBean protected GoogleTokenVerifier googleTokenVerifier;

  protected User verifiedUser() {
    return verifiedUser("act-" + UUID.randomUUID());
  }

  protected User verifiedUser(String suffix) {
    User user = new User();
    user.setEmail(suffix + "-" + System.nanoTime() + "@example.com");
    user.setPasswordHash(passwordEncoder.encode("Password1"));
    user.setDisplayName("ACT Test User");
    user.setTimezone("Asia/Ho_Chi_Minh");
    user.setAuthProvider(AuthProvider.LOCAL);
    user.setEmailVerified(true);
    return users.saveAndFlush(user);
  }

  protected String accessToken(User user) {
    return tokenService.issue(user).accessToken();
  }
}
