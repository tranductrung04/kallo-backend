package com.healthcare.kallo.controller.game;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.game.RespondFriendRequest;
import com.healthcare.kallo.dto.request.game.SendFriendRequest;
import com.healthcare.kallo.dto.response.game.FriendPageResponse;
import com.healthcare.kallo.dto.response.game.FriendRequestResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.game.FriendService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
  private final FriendService friends;
  public FriendController(FriendService friends) { this.friends = friends; }

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FriendRequestResponse> send(@Valid @RequestBody SendFriendRequest request, Authentication authentication) {
    return ApiResponse.success(friends.sendRequest(CurrentUser.id(authentication), request));
  }

  @GetMapping("/requests")
  public ApiResponse<List<FriendRequestResponse>> requests(@RequestParam(defaultValue = "incoming") String direction,
      Authentication authentication) {
    if (!direction.equals("incoming") && !direction.equals("outgoing"))
      throw new com.healthcare.kallo.common.BusinessException(com.healthcare.kallo.common.ErrorCode.INVALID_PARAMETER);
    return ApiResponse.success(friends.listRequests(CurrentUser.id(authentication), direction.equals("incoming")));
  }

  @PutMapping("/requests/{id}")
  public ApiResponse<FriendRequestResponse> respond(@PathVariable UUID id, @Valid @RequestBody RespondFriendRequest request,
      Authentication authentication) {
    return ApiResponse.success(friends.respond(CurrentUser.id(authentication), id, request));
  }

  @DeleteMapping("/requests/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable UUID id, Authentication authentication) {
    friends.cancel(CurrentUser.id(authentication), id);
  }

  @GetMapping
  public ApiResponse<FriendPageResponse> list(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size, Authentication authentication) {
    return ApiResponse.success(friends.listFriends(CurrentUser.id(authentication), page, size));
  }

  @DeleteMapping("/{friendUserId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unfriend(@PathVariable UUID friendUserId, Authentication authentication) {
    friends.unfriend(CurrentUser.id(authentication), friendUserId);
  }
}
