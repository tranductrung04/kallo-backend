package com.healthcare.kallo.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.model.auth.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HealthSyncIntegrationTest extends ActIntegrationTestBase {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void repeatedHeartRateBucketIsUnchangedAndRefreshesLastSync() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    String body = request(List.of(record("HEART_RATE", "HR:bucket-1", "71.8333333", "70", "75")));

    JsonNode first = data(postSync(token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    JsonNode second = data(postSync(token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(first.get("created").asInt()).isEqualTo(1);
    assertThat(second.get("updated").asInt()).isZero();
    assertThat(second.get("unchanged").asInt()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "select value from health_sync_logs where user_id = ? and external_id = ?",
        BigDecimal.class, user.getId(), "HR:bucket-1")).isEqualByComparingTo("71.83");
  }

  @Test
  void rejectsBadRecordsWithoutRollingBackValidRecords() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    String body = request(List.of(
        record("HEART_RATE", "HR:valid", "71.8333333", "70", "75"),
        record("HEART_RATE", "HR:bad", "71", "70", "1000000000000"),
        record("STEPS", "STEPS:bad", "10", "0", "10"),
        "{\"dataType\":\"STEPS\",\"externalId\":\"\",\"value\":10}",
        "{\"dataType\":\"STEPS\",\"externalId\":\"STEPS:no-start\",\"endTime\":\""
            + Instant.now() + "\",\"value\":10}"));

    JsonNode result = data(postSync(token, body).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString());
    assertThat(result.get("created").asInt()).isEqualTo(1);
    assertThat(result.get("rejected").size()).isEqualTo(4);
    assertThat(jdbcTemplate.queryForObject("select count(*) from health_sync_logs where user_id = ?",
        Long.class, user.getId())).isEqualTo(1L);
  }

  @Test
  void statusIsEmptyBeforeFirstSyncAndBatchLimitIsEnforced() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    JsonNode empty = data(mockMvc.perform(get("/api/health-sync/status")
        .header("Authorization", bearer(token))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertThat(empty.size()).isZero();

    List<String> records = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      records.add(record("STEPS", "STEPS:" + i, "100", null, null));
    }
    postSync(token, request(records)).andExpect(status().isPayloadTooLarge());
  }

  @Test
  void healthConnectStepsTakePriorityOverDeviceSensorForSameHour() throws Exception {
    User user = verifiedUser();
    String token = accessToken(user);
    Instant start = Instant.now().minusSeconds(60);
    String healthConnect = recordAt("STEPS", "STEPS:priority-hc", "1000", start);
    String sensor = recordAt("STEPS", "STEPS:priority-sensor", "2000", start);
    postSync(token, request("HEALTH_CONNECT", List.of(healthConnect)))
        .andExpect(status().isOk());
    postSync(token, request("DEVICE_SENSOR", List.of(sensor)))
        .andExpect(status().isOk());

    JsonNode summary = data(mockMvc.perform(get("/api/activities/summary")
        .header("Authorization", bearer(token))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertThat(summary.get(0).get("totalSteps").asLong()).isEqualTo(1000);
  }

  private org.springframework.test.web.servlet.ResultActions postSync(String token, String body)
      throws Exception {
    return mockMvc.perform(post("/api/health-sync").header("Authorization", bearer(token))
        .contentType(APPLICATION_JSON).content(body));
  }

  private String request(List<String> records) {
    return request("HEALTH_CONNECT", records);
  }

  private String request(String provider, List<String> records) {
    return "{\"provider\":\"" + provider + "\",\"records\":[" + String.join(",", records) + "]}";
  }

  private String record(String type, String externalId, String value, String min, String max) {
    return "{\"dataType\":\"" + type + "\",\"externalId\":\"" + externalId
        + "\",\"startTime\":\"" + Instant.now().minusSeconds(60)
        + "\",\"endTime\":\"" + Instant.now() + "\",\"value\":" + value
        + (min == null ? "" : ",\"min\":" + min)
        + (max == null ? "" : ",\"max\":" + max) + "}";
  }

  private String recordAt(String type, String externalId, String value, Instant start) {
    return "{\"dataType\":\"" + type + "\",\"externalId\":\"" + externalId
        + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + start.plusSeconds(60)
        + "\",\"value\":" + value + "}";
  }

  private JsonNode data(String response) throws Exception {
    return objectMapper.readTree(response).get("data");
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
