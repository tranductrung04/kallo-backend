package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.response.game.UserSummaryResponse;
import java.util.List;
import java.util.UUID;

public interface UserSearchService {
  List<UserSummaryResponse> search(UUID userId, String query);
}
