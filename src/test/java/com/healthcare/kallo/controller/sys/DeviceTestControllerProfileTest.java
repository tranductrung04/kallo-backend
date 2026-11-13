package com.healthcare.kallo.controller.sys;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.kallo.controller.sys.DeviceTestController;
import com.healthcare.kallo.service.sys.PushDispatcher;
import com.healthcare.kallo.config.RateLimitTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = DeviceTestController.class,
    properties = "spring.profiles.active=test")
@AutoConfigureMockMvc(addFilters = false)
@Import({DeviceTestController.class, RateLimitTestConfig.class})
class DeviceTestControllerProfileTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private PushDispatcher pushes;

  @Test
  void testPushEndpointIsUnavailableOutsideDevProfile() throws Exception {
    mockMvc.perform(post("/api/devices/test-push")).andExpect(status().isNotFound());
  }
}
