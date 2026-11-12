package com.healthcare.kallo.controller.sys;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.healthcare.kallo.dto.response.sys.NotificationPageResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.sys.NotificationQuery;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
  @Mock NotificationQuery query;
  MockMvc mvc;
  UUID userId;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new NotificationController(query)).build();
    userId = UUID.randomUUID();
  }

  @Test
  void unreadCountUsesCountQueryInsteadOfLoadingPage() throws Exception {
    when(query.unreadCount(userId)).thenReturn(7L);
    mvc.perform(get("/api/notifications/unread-count").principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(7));

    verify(query).unreadCount(eq(userId));
    verify(query, never()).page(any(), any(), anyInt(), anyBoolean());
  }

  private UsernamePasswordAuthenticationToken authentication() {
    Jwt jwt = Jwt.withTokenValue("token").subject(userId.toString()).header("alg", "none").build();
    return new UsernamePasswordAuthenticationToken(jwt, "token");
  }
}
