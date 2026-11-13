package com.healthcare.kallo.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.dto.request.act.CreateWeightLogRequest;
import com.healthcare.kallo.event.act.WeightChangedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.repository.act.WeightLogRepository;
import com.healthcare.kallo.service.act.WeightLogService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import tools.jackson.databind.ObjectMapper;

@RecordApplicationEvents
class WeightLogIntegrationTest extends ActIntegrationTestBase {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired WeightLogService weightLogs;
  @Autowired WeightLogRepository weightLogRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ApplicationEvents applicationEvents;

  @Test
  void validatesWeightBoundariesAndTimestampRange() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);

    postWeight(token, "19.9", Instant.now()).andExpect(status().isBadRequest());
    postWeight(token, "20.0", Instant.now()).andExpect(status().isCreated());
    postWeight(token, "300.0", Instant.now()).andExpect(status().isCreated());
    postWeight(token, "300.1", Instant.now()).andExpect(status().isBadRequest());
    postWeight(token, "70.0", Instant.now().plusSeconds(600))
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("TIMESTAMP_OUT_OF_RANGE"));
    postWeight(token, "70.0", Instant.now().plusSeconds(120)).andExpect(status().isCreated());
    postWeight(token, "70.0", Instant.now().minusSeconds(11L * 365 * 24 * 60 * 60))
        .andExpect(status().isBadRequest());
  }

  @Test
  void repeatedPostReturnsCreatedThenOkWithSameId() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    UUID clientUuid = UUID.randomUUID();
    String body = createBody(clientUuid, "70.0", Instant.now());

    String first =
        mockMvc
            .perform(post("/api/weight-logs").header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    String second =
        mockMvc
            .perform(post("/api/weight-logs").header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(objectMapper.readTree(first).at("/data/id").asText())
        .isEqualTo(objectMapper.readTree(second).at("/data/id").asText());
  }

  @Test
  void concurrentPostsCreateOnlyOneRow() throws Exception {
    User user = verifiedUser();
    for (int attempt = 0; attempt < 20; attempt++) {
      UUID clientUuid = UUID.randomUUID();
      CreateWeightLogRequest request =
          new CreateWeightLogRequest(clientUuid, BigDecimal.valueOf(70), Instant.now(), null);
      CyclicBarrier barrier = new CyclicBarrier(2);
      try (var executor = Executors.newFixedThreadPool(2)) {
        Callable<WeightLogService.CreateResult> task =
            () -> {
              barrier.await();
              return weightLogs.create(user.getId(), request);
            };
        List<Future<WeightLogService.CreateResult>> results =
            executor.invokeAll(List.of(task, task));
        assertThat(List.of(results.get(0).get().created(), results.get(1).get().created()))
            .containsExactlyInAnyOrder(true, false);
      }
      assertThat(jdbcTemplate.queryForObject(
              "select count(*) from weight_logs where user_id = ? and client_uuid = ?",
              Long.class, user.getId(), clientUuid))
          .isEqualTo(1L);
    }
  }

  @Test
  void foreignUserCannotReadUpdateOrDeleteAnotherUsersLog() throws Exception {
    User owner = verifiedUser();
    User stranger = verifiedUser();
    String ownerToken = accessToken(owner);
    String strangerToken = accessToken(stranger);
    String created =
        mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(ownerToken))
                .contentType(APPLICATION_JSON)
                .content(createBody(UUID.randomUUID(), "70.0", Instant.now())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = objectMapper.readTree(created).at("/data/id").asText();

    mockMvc.perform(get("/api/weight-logs/" + id).header("Authorization", bearer(strangerToken)))
        .andExpect(status().isNotFound());
    mockMvc.perform(put("/api/weight-logs/" + id).header("Authorization", bearer(strangerToken))
            .contentType(APPLICATION_JSON)
            .content(updateBody("71.0", Instant.now(), null)))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/weight-logs/" + id).header("Authorization", bearer(strangerToken)))
        .andExpect(status().isNotFound());
    assertThat(weightLogRepository.findById(UUID.fromString(id)).orElseThrow().getWeightKg())
        .isEqualByComparingTo("70.0");
    assertThat(weightLogRepository.findById(UUID.fromString(id)).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void softDeletedLogIsHiddenAndGetReturnsNotFound() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    String created =
        mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(createBody(UUID.randomUUID(), "70.0", Instant.now())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = objectMapper.readTree(created).at("/data/id").asText();

    mockMvc.perform(delete("/api/weight-logs/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/weight-logs/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/weight-logs/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/weight-logs").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(id));
    mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(createBody(UUID.fromString(objectMapper.readTree(created).at("/data/clientUuid").asText()),
                "71.0", Instant.now())))
        .andExpect(status().isConflict());
    mockMvc.perform(put("/api/weight-logs/" + id).header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(updateBody("71.0", Instant.now(), null)))
        .andExpect(status().isConflict());
  }

  @Test
  void staleUpdateIsIgnoredAndChangingDayUpdatesBothDays() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    ZoneId zone = ZoneId.of(user.getTimezone());
    LocalDate today = LocalDate.now(zone);
    Instant original = today.minusDays(1).atTime(23, 0).atZone(zone).toInstant();
    Instant moved = today.atTime(1, 0).atZone(zone).toInstant();
    String created =
        mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
                .contentType(APPLICATION_JSON)
                .content(createBody(UUID.randomUUID(), "70.0", original)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = objectMapper.readTree(created).at("/data/id").asText();
    String updatedAt = objectMapper.readTree(created).at("/data/updatedAt").asText();

    mockMvc.perform(put("/api/weight-logs/" + id).header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(updateBody("75.0", moved, "2000-01-01T00:00:00Z")))
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("\"weightKg\":70.0"));

    mockMvc.perform(put("/api/weight-logs/" + id).header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(updateBody("75.0", moved, updatedAt)))
        .andExpect(status().isOk());
    assertThat(objectMapper.readTree(
        mockMvc.perform(get("/api/weight-logs/" + id).header("Authorization", bearer(token)))
            .andReturn().getResponse().getContentAsString()).at("/data/weightKg").asDouble())
        .isEqualTo(75.0);
    assertThat(applicationEvents.stream(WeightChangedEvent.class)
            .map(WeightChangedEvent.class::cast)
            .anyMatch(event -> event.localDate().equals(original.atZone(ZoneId.of(user.getTimezone())).toLocalDate())))
        .isTrue();
    assertThat(applicationEvents.stream(WeightChangedEvent.class)
            .map(WeightChangedEvent.class::cast)
            .anyMatch(event -> event.localDate().equals(moved.atZone(zone).toLocalDate())))
        .isTrue();
  }

  @Test
  void localDateRangeExcludesMidnightOfFollowingDay() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    ZoneId zone = ZoneId.of(user.getTimezone());
    LocalDate day = LocalDate.now(zone).minusDays(2);
    Instant dayStart = day.atStartOfDay(zone).toInstant();
    Instant nextDayStart = day.plusDays(1).atStartOfDay(zone).toInstant();
    WeightLogService.CreateResult first =
        weightLogs.create(user.getId(),
            new CreateWeightLogRequest(UUID.randomUUID(), BigDecimal.valueOf(70), dayStart, null));
    WeightLogService.CreateResult second =
        weightLogs.create(user.getId(),
            new CreateWeightLogRequest(UUID.randomUUID(), BigDecimal.valueOf(71), nextDayStart, null));

    String response =
        mockMvc.perform(get("/api/weight-logs").param("from", day.toString()).param("to", day.toString())
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(response).contains(first.response().id().toString());
    assertThat(response).doesNotContain(second.response().id().toString());
  }

  @Test
  void rejectsInvalidDateRangesAndMalformedRequests() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    mockMvc.perform(get("/api/weight-logs").param("from", "2026-11-12").param("to", "2026-11-11")
            .header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/weight-logs").param("from", "2025-01-01").param("to", "2026-01-02")
            .header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/weight-logs").param("from", "2026-11-11")
            .header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest());
    String note = "x".repeat(256);
    mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(createBody(UUID.randomUUID(), "70.0", Instant.now()).replace("}", ",\"note\":\"" + note + "\"}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unverifiedEmailIsForbidden() throws Exception {
    User user = verifiedUser();
    user.setEmailVerified(false);
    users.saveAndFlush(user);
    String token = tokenService.issue(user).accessToken();

    mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(createBody(UUID.randomUUID(), "70.0", Instant.now())))
        .andExpect(status().isForbidden())
        .andExpect(result -> assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
            .at("/error/code").asText()).isEqualTo("EMAIL_NOT_VERIFIED"));
  }

  @Test
  void deletingAccountCascadesWeightLogs() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON)
            .content(createBody(UUID.randomUUID(), "70.0", Instant.now())))
        .andExpect(status().isCreated());

    mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(token))
            .contentType(APPLICATION_JSON).content("{\"password\":\"Password1\"}"))
        .andExpect(status().isNoContent());
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from weight_logs where user_id = ?", Long.class, user.getId()))
        .isZero();
  }

  private org.springframework.test.web.servlet.ResultActions postWeight(
      String token, String weight, Instant loggedAt) throws Exception {
    return mockMvc.perform(post("/api/weight-logs").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON)
        .content(createBody(UUID.randomUUID(), weight, loggedAt)));
  }

  private String createBody(UUID clientUuid, String weight, Instant loggedAt) {
    return "{\"clientUuid\":\"" + clientUuid + "\",\"weightKg\":" + weight
        + ",\"loggedAt\":\"" + loggedAt + "\"}";
  }

  private String updateBody(String weight, Instant loggedAt, String clientUpdatedAt) {
    return "{\"weightKg\":" + weight + ",\"loggedAt\":\"" + loggedAt + "\""
        + (clientUpdatedAt == null ? "" : ",\"clientUpdatedAt\":\"" + clientUpdatedAt + "\"") + "}";
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
