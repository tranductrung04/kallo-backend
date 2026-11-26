package com.healthcare.kallo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthcare.kallo.model.profile.UserHealthProfile;
import com.healthcare.kallo.model.profile.enums.Gender;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.repository.profile.UserHealthProfileRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConditionalActProfileBodyMetricsTest {

  @Test
  void readsHeightCmAndGenderFromHealthProfile() {
    UserHealthProfileRepository healthProfiles = mock(UserHealthProfileRepository.class);
    WeightLogRepository weightLogs = mock(WeightLogRepository.class);
    UUID userId = UUID.randomUUID();

    UserHealthProfile profile = new UserHealthProfile();
    profile.setUserId(userId);
    profile.setHeightCm(new BigDecimal("168.50"));
    profile.setGender(Gender.FEMALE);
    when(healthProfiles.findById(userId)).thenReturn(Optional.of(profile));

    var metrics = new ConditionalActProfileBodyMetrics().profileBodyMetrics(healthProfiles, weightLogs);

    assertThat(metrics.heightCm(userId)).contains(new BigDecimal("168.50"));
    assertThat(metrics.gender(userId)).contains("female");
  }
}
