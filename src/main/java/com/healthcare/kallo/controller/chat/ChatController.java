package com.healthcare.kallo.controller.chat;

import com.healthcare.kallo.common.ApiResponse;
import com.healthcare.kallo.dto.request.chat.ConversationUpdateRequest;
import com.healthcare.kallo.dto.request.chat.SendMessageRequest;
import com.healthcare.kallo.dto.response.chat.ChatReplyResponse;
import com.healthcare.kallo.dto.response.chat.ConversationPageResponse;
import com.healthcare.kallo.dto.response.chat.ConversationResponse;
import com.healthcare.kallo.dto.response.chat.MessagePageResponse;
import com.healthcare.kallo.security.auth.CurrentUser;
import com.healthcare.kallo.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Nutrition chatbot")
public class ChatController {
  private final ChatService chat;

  public ChatController(ChatService chat) {
    this.chat = chat;
  }

  @PostMapping("/messages")
  @Operation(summary = "Send chat message")
  public ApiResponse<ChatReplyResponse> send(@Valid @RequestBody SendMessageRequest request,
      Authentication authentication) {
    return ApiResponse.success(chat.sendMessage(CurrentUser.id(authentication), request));
  }

  @GetMapping("/conversations")
  @Operation(summary = "List user conversations")
  public ApiResponse<ConversationPageResponse> listConversations(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return ApiResponse.success(chat.listConversations(CurrentUser.id(authentication), page, size));
  }

  @PostMapping("/conversations")
  @Operation(summary = "Create empty conversation")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<ConversationResponse> createConversation(Authentication authentication) {
    return ApiResponse.success(chat.createConversation(CurrentUser.id(authentication)));
  }

  @GetMapping("/conversations/{id}")
  @Operation(summary = "Get conversation")
  public ApiResponse<ConversationResponse> getConversation(@PathVariable UUID id,
      Authentication authentication) {
    return ApiResponse.success(chat.getConversation(CurrentUser.id(authentication), id));
  }

  @PatchMapping("/conversations/{id}")
  @Operation(summary = "Rename conversation")
  public ApiResponse<ConversationResponse> renameConversation(@PathVariable UUID id,
      @Valid @RequestBody ConversationUpdateRequest request, Authentication authentication) {
    return ApiResponse.success(chat.renameConversation(CurrentUser.id(authentication), id, request));
  }

  @DeleteMapping("/conversations/{id}")
  @Operation(summary = "Delete conversation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteConversation(@PathVariable UUID id, Authentication authentication) {
    chat.deleteConversation(CurrentUser.id(authentication), id);
  }

  @GetMapping("/conversations/{id}/messages")
  @Operation(summary = "List conversation messages")
  public ApiResponse<MessagePageResponse> listMessages(@PathVariable UUID id,
      @RequestParam(required = false) String before, @RequestParam(defaultValue = "30") int limit,
      Authentication authentication) {
    return ApiResponse.success(chat.listMessages(CurrentUser.id(authentication), id, before, limit));
  }
}
