package com.healthcare.kallo.service.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import com.healthcare.kallo.service.profile.impl.HealthProfileStatusProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthProfileStatusProviderTest {

  @Mock private UserHealthProfileRepository healthProfileRepository;
  private HealthProfileStatusProvider provider;

  @BeforeEach
  void setUp() {
    provider = new HealthProfileStatusProvider(healthProfileRepository);
  }

  @Test
  void isCompleted_shouldReturnTrue_whenProfileExists() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    when(healthProfileRepository.existsById(userId)).thenReturn(true);

    assertThat(provider.isCompleted(user)).isTrue();
  }

  @Test
  void isCompleted_shouldReturnFalse_whenProfileDoesNotExist() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    when(healthProfileRepository.existsById(userId)).thenReturn(false);

    assertThat(provider.isCompleted(user)).isFalse();
  }

  @Test
  void isCompleted_shouldReturnFalse_whenUserOrIdIsNull() {
    assertThat(provider.isCompleted(null)).isFalse();
    assertThat(provider.isCompleted(new User())).isFalse();
  }
}
