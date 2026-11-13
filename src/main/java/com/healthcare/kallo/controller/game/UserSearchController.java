package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.response.game.UserSummaryResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.UserSearchService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {
  private final UserSearchService search;
  public UserSearchController(UserSearchService search) { this.search = search; }

  @GetMapping("/search")
  public ApiResponse<List<UserSummaryResponse>> search(@RequestParam String q, Authentication authentication) {
    return ApiResponse.success(search.search(CurrentUser.id(authentication), q));
  }
}
