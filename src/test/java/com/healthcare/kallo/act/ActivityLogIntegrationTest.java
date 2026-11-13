package com.healthcare.kallo.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.event.act.ActivityDataChangedEvent;
import com.healthcare.kallo.model.auth.User;
import com.healthcare.kallo.service.act.ActivityLogService;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RecordApplicationEvents
class ActivityLogIntegrationTest extends ActIntegrationTestBase {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired ActivityLogService activityLogs;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ApplicationEvents applicationEvents;

  @Test
  void createsActivityThroughHttpAndUsesWeightSnapshot() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    Instant started = Instant.now().minusSeconds(30);
    postWeight(user, "70.0", started.minusSeconds(60));

    String created = postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE",
        started, 30, null)
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    assertThat(data(created).get("caloriesBurned").asDouble()).isEqualTo(290.5);
    assertThat(data(created).get("weightKgUsed").asDouble()).isEqualTo(70.0);

    postWeight(user, "90.0", Instant.now());
    String id = data(created).get("id").asText();
    String fetched = mockMvc.perform(get("/api/activities/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(data(fetched).get("weightKgUsed").asDouble()).isEqualTo(70.0);
  }

  @Test
  void acceptsLegacyFrontendActivityPayloadAndLegacyDeleteId() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    postWeight(user, "70.0", Instant.now().minusSeconds(120));
    String legacyId = "act-" + System.currentTimeMillis() + "-abc12";
    String payload = "{\"id\":\"" + legacyId
        + "\",\"date\":\"2026-11-11\",\"type\":\"running\",\"name\":\"Chạy bộ\","
        + "\"durationMinutes\":30,\"caloriesBurned\":999,\"source\":\"MANUAL\","
        + "\"timestamp\":\"" + Instant.now().minusSeconds(30) + "\"}";

    mockMvc.perform(post("/api/activities").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString())
            .contains("\"activityType\":\"RUN_8KMH\"")
            .doesNotContain("\"caloriesBurned\":999"));

    mockMvc.perform(delete("/api/activities/" + legacyId)
        .header("Authorization", bearer(token))).andExpect(status().isNoContent());
  }

  @Test
  void requiresWeightAndRejectsUnsupportedTypeIntensityDistanceAndBounds() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    Instant started = Instant.now().minusSeconds(30);

    postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE", started, 30, null)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString())
            .contains("WEIGHT_REQUIRED"));

    postWeight(user, "70.0", Instant.now().minusSeconds(60));
    postActivity(token, UUID.randomUUID(), "RUN_8KMH", "LIGHT", started, 30, null)
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString())
            .contains("\"field\":\"intensity\""));
    postActivity(token, UUID.randomUUID(), "UNKNOWN", "MODERATE", started, 30, null)
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString())
            .contains("ACTIVITY_TYPE_UNKNOWN"));
    postActivity(token, UUID.randomUUID(), "YOGA_HATHA", "MODERATE", started, 30, 1.0)
        .andExpect(status().isBadRequest())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString())
            .contains("\"field\":\"distanceKm\""));
    postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE", started, 0, null)
        .andExpect(status().isBadRequest());
    postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE", started, 1441, null)
        .andExpect(status().isBadRequest());
    postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE", started, 30, 10000.0)
        .andExpect(status().isBadRequest());
  }

  @Test
  void repeatedAndConcurrentPostsAreIdempotentAndDeletedRecordIsRejected() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    postWeight(user, "70.0", Instant.now().minusSeconds(60));
    UUID clientUuid = UUID.randomUUID();
    String body = activityBody(clientUuid, "RUN_8KMH", Instant.now().minusSeconds(30), 30, null);
    String first = mockMvc.perform(post("/api/activities").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(body)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String second = mockMvc.perform(post("/api/activities").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(data(first).get("id").asText()).isEqualTo(data(second).get("id").asText());

    UUID concurrentUuid = UUID.randomUUID();
    var request = new com.healthcare.kallo.dto.request.act.CreateActivityRequest(
        concurrentUuid, "RUN_8KMH",
        com.healthcare.kallo.model.act.enums.Intensity.MODERATE,
        Instant.now().minusSeconds(30), 30, null, null);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<ActivityLogService.CreateResult> task = () -> {
        barrier.await();
        return activityLogs.create(user.getId(), request);
      };
      List<Future<ActivityLogService.CreateResult>> results =
          executor.invokeAll(List.of(task, task));
      assertThat(List.of(results.get(0).get().created(), results.get(1).get().created()))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from activity_logs where user_id = ? and client_uuid = ?",
        Long.class, user.getId(), concurrentUuid)).isEqualTo(1L);

    String id = data(first).get("id").asText();
    mockMvc.perform(delete("/api/activities/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    postActivity(token, clientUuid, "RUN_8KMH", "MODERATE", Instant.now().minusSeconds(30), 30, null)
        .andExpect(status().isConflict());
  }

  @Test
  void supportsPutRecalculationAndProtectsAgainstIdor() throws Exception {
    User owner = verifiedUser();
    User stranger = verifiedUser();
    String ownerToken = accessToken(owner);
    String strangerToken = accessToken(stranger);
    postWeight(owner, "70.0", Instant.now().minusSeconds(60));
    String created = postActivity(ownerToken, UUID.randomUUID(), "RUN_8KMH", "MODERATE",
        Instant.now().minusSeconds(30), 30, null).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String id = data(created).get("id").asText();

    mockMvc.perform(get("/api/activities/" + id).header("Authorization", bearer(strangerToken)))
        .andExpect(status().isNotFound());
    mockMvc.perform(put("/api/activities/" + id).header("Authorization", bearer(strangerToken))
        .contentType(APPLICATION_JSON).content(updateBody("RUN_8KMH", Instant.now().minusSeconds(30), 60)))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete("/api/activities/" + id).header("Authorization", bearer(strangerToken)))
        .andExpect(status().isNotFound());

    String updated = mockMvc.perform(put("/api/activities/" + id).header("Authorization", bearer(ownerToken))
        .contentType(APPLICATION_JSON).content(updateBody("RUN_8KMH", Instant.now().minusSeconds(30), 60)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(data(updated).get("caloriesBurned").asDouble()).isEqualTo(581.0);
  }

  @Test
  void filtersByLocalDayPaginatesActiveTypesAndPublishesBothChangedDays() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    ZoneId zone = ZoneId.of(user.getTimezone());
    LocalDate day = LocalDate.now(zone).minusDays(2);
    Instant late = day.atTime(23, 30).atZone(zone).toInstant();
    postWeight(user, "70.0", late.minusSeconds(60));
    String created = postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE", late, 30, null)
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String id = data(created).get("id").asText();
    mockMvc.perform(get("/api/activities").param("from", day.toString()).param("to", day.toString())
        .param("page", "0").param("size", "1")
        .header("Authorization", bearer(token))).andExpect(status().isOk())
        .andExpect(result -> {
          JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
          assertThat(page.get("items").size()).isEqualTo(1);
          assertThat(page.get("totalElements").asLong()).isEqualTo(1);
          assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(id);
        });

    LocalDate movedDay = day.plusDays(1);
    mockMvc.perform(put("/api/activities/" + id).header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(updateBody("RUN_8KMH",
            movedDay.atTime(1, 0).atZone(zone).toInstant(), 30))).andExpect(status().isOk());
    assertThat(applicationEvents.stream(ActivityDataChangedEvent.class).map(ActivityDataChangedEvent.class::cast)
        .map(ActivityDataChangedEvent::localDate)).contains(day, movedDay);

    jdbcTemplate.update("update activity_types set active = false where code = 'SOCCER'");
    String types = mockMvc.perform(get("/api/activity-types").header("Authorization", bearer(token)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(types).doesNotContain("SOCCER");
  }

  @Test
  void deletingAccountCascadesActivityLogs() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    postWeight(user, "70.0", Instant.now().minusSeconds(60));
    postActivity(token, UUID.randomUUID(), "RUN_8KMH", "MODERATE",
        Instant.now().minusSeconds(30), 30, null).andExpect(status().isCreated());
    postSyncForCascade(token);

    mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content("{\"password\":\"Password1\"}"))
        .andExpect(status().isNoContent());
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from weight_logs where user_id = ?", Long.class, user.getId())).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from activity_logs where user_id = ?", Long.class, user.getId())).isZero();
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from health_sync_logs where user_id = ?", Long.class, user.getId())).isZero();
  }

  private void postSyncForCascade(String token) throws Exception {
    mockMvc.perform(post("/api/health-sync").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content("""
            {"provider":"HEALTH_CONNECT","records":[{
              "dataType":"STEPS","externalId":"STEPS:cascade","value":10,
              "startTime":"2026-11-11T10:00:00Z","endTime":"2026-11-11T11:00:00Z"}]}
            """)).andExpect(status().isOk());
  }

  @Test
  void validatesPolylinePrecisionRouteSpeedAndOwnerAccess() throws Exception {
    User owner = verifiedUser();
    User stranger = verifiedUser();
    String ownerToken = accessToken(owner);
    String route = encodePolyline(List.of(new double[] {10.00000, 106.00000},
        new double[] {10.00100, 106.00100}));
    postWeight(owner, "70.0", Instant.now().minusSeconds(60));
    String body = "{\"clientUuid\":\"" + UUID.randomUUID()
        + "\",\"activityType\":\"RUN_8KMH\",\"source\":\"GPS\",\"startedAt\":\""
        + Instant.now().minusSeconds(30)
        + "\",\"durationMin\":30,\"routePolyline\":\"" + route + "\"}";
    String created = mockMvc.perform(post("/api/activities").header("Authorization", bearer(ownerToken))
        .contentType(APPLICATION_JSON).content(body)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String id = data(created).get("id").asText();
    assertThat(created).doesNotContain("routePolyline");
    String detail = mockMvc.perform(get("/api/activities/" + id)
        .header("Authorization", bearer(ownerToken))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(data(detail).get("routePolyline").asText()).isEqualTo(route);
    mockMvc.perform(get("/api/activities/" + id)
        .header("Authorization", bearer(accessToken(stranger))))
        .andExpect(status().isNotFound());

    String tooFast = "{\"clientUuid\":\"" + UUID.randomUUID()
        + "\",\"activityType\":\"RUN_8KMH\",\"source\":\"GPS\",\"startedAt\":\""
        + Instant.now().minusSeconds(30)
        + "\",\"durationMin\":30,\"routePolyline\":\""
        + encodePolyline(List.of(new double[] {10, 106}, new double[] {10, 107})) + "\"}";
    mockMvc.perform(post("/api/activities").header("Authorization", bearer(ownerToken))
        .contentType(APPLICATION_JSON).content(tooFast)).andExpect(status().isBadRequest());

    String impossibleCoordinates = "{\"clientUuid\":\"" + UUID.randomUUID()
        + "\",\"activityType\":\"RUN_8KMH\",\"source\":\"GPS\",\"startedAt\":\""
        + Instant.now().minusSeconds(30)
        + "\",\"durationMin\":30,\"routePolyline\":\""
        + encodePolyline(List.of(new double[] {200, 300}, new double[] {800, -700})) + "\"}";
    mockMvc.perform(post("/api/activities").header("Authorization", bearer(ownerToken))
        .contentType(APPLICATION_JSON).content(impossibleCoordinates)).andExpect(status().isBadRequest());

    String oversized = "{\"clientUuid\":\"" + UUID.randomUUID()
        + "\",\"activityType\":\"RUN_8KMH\",\"source\":\"GPS\",\"startedAt\":\""
        + Instant.now().minusSeconds(30)
        + "\",\"durationMin\":30,\"routePolyline\":\"" + "?".repeat(102401) + "\"}";
    mockMvc.perform(post("/api/activities").header("Authorization", bearer(ownerToken))
        .contentType(APPLICATION_JSON).content(oversized)).andExpect(status().isPayloadTooLarge());
  }

  @Test
  void softDeletingActivityRemovesRoutePolyline() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    postWeight(user, "70.0", Instant.now().minusSeconds(60));
    String body = "{\"clientUuid\":\"" + UUID.randomUUID()
        + "\",\"activityType\":\"RUN_8KMH\",\"source\":\"GPS\",\"startedAt\":\""
        + Instant.now().minusSeconds(30)
        + "\",\"durationMin\":30,\"routePolyline\":\""
        + encodePolyline(List.of(new double[] {10, 106}, new double[] {10.001, 106.001})) + "\"}";
    String created = mockMvc.perform(post("/api/activities").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(body)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String id = data(created).get("id").asText();
    mockMvc.perform(delete("/api/activities/" + id).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    assertThat(jdbcTemplate.queryForObject(
        "select route_polyline from activity_logs where id = ?", String.class, UUID.fromString(id)))
        .isNull();
  }

  private String encodePolyline(List<double[]> points) {
    StringBuilder result = new StringBuilder();
    long lastLat = 0, lastLon = 0;
    for (double[] point : points) {
      long lat = Math.round(point[0] * 100000);
      long lon = Math.round(point[1] * 100000);
      appendEncoded(result, lat - lastLat);
      appendEncoded(result, lon - lastLon);
      lastLat = lat;
      lastLon = lon;
    }
    return result.toString();
  }

  private void appendEncoded(StringBuilder output, long value) {
    long encoded = value < 0 ? ~(value << 1) : value << 1;
    while (encoded >= 0x20) {
      output.append((char) (((encoded & 0x1f) | 0x20) + 63));
      encoded >>= 5;
    }
    output.append((char) (encoded + 63));
  }

  private org.springframework.test.web.servlet.ResultActions postActivity(String token, UUID clientUuid,
      String type, String intensity, Instant startedAt, int duration, Double distance) throws Exception {
    String body = activityBody(clientUuid, type, startedAt, duration, distance)
        .replace("\"intensity\":\"MODERATE\"", "\"intensity\":\"" + intensity + "\"");
    return mockMvc.perform(post("/api/activities").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(body));
  }

  private void postWeight(User user, String weight, Instant loggedAt) {
    activityLogsWeight(user, UUID.randomUUID(), new java.math.BigDecimal(weight), loggedAt);
  }

  private void activityLogsWeight(User user, UUID clientUuid, java.math.BigDecimal weight, Instant loggedAt) {
    jdbcTemplate.update("""
        insert into weight_logs (user_id, client_uuid, weight_kg, logged_at, created_at, updated_at)
        values (?, ?, ?, ?, now(), now())
        """, user.getId(), clientUuid, weight, Timestamp.from(loggedAt));
  }

  private String activityBody(UUID clientUuid, String type, Instant startedAt, int duration, Double distance) {
    return "{\"clientUuid\":\"" + clientUuid + "\",\"activityType\":\"" + type
        + "\",\"intensity\":\"MODERATE\",\"startedAt\":\"" + startedAt + "\",\"durationMin\":" + duration
        + (distance == null ? "" : ",\"distanceKm\":" + distance) + "}";
  }

  private String updateBody(String type, Instant startedAt, int duration) {
    return "{\"activityType\":\"" + type + "\",\"intensity\":\"MODERATE\",\"startedAt\":\""
        + startedAt + "\",\"durationMin\":" + duration + "}";
  }

  private JsonNode data(String response) throws Exception {
    return objectMapper.readTree(response).get("data");
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
