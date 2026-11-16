package com.healthcare.kallo.service.game;

import com.healthcare.kallo.dto.request.game.CreateBadgeRequest;
import com.healthcare.kallo.dto.request.game.UpdateBadgeRequest;
import com.healthcare.kallo.dto.response.game.AdminBadgeResponse;
import java.util.List;
import java.util.UUID;

public interface AdminBadgeService {
  List<AdminBadgeResponse> list();
  AdminBadgeResponse create(CreateBadgeRequest request);
  AdminBadgeResponse update(UUID badgeId, UpdateBadgeRequest request);
}
