package com.healthcare.kallo.repository.chat;

import com.healthcare.kallo.model.chat.ChatMessage;
import com.healthcare.kallo.model.chat.enums.ChatMessageStatus;
import com.healthcare.kallo.model.chat.enums.ChatRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
  Optional<ChatMessage> findByConversationIdAndClientMessageId(UUID conversationId, UUID clientMessageId);

  Optional<ChatMessage> findByConversationIdAndParentMessageIdAndRole(UUID conversationId,
      UUID parentMessageId, ChatRole role);

  Optional<ChatMessage> findByConversationIdAndStatus(UUID conversationId, ChatMessageStatus status);

  List<ChatMessage> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);

  List<ChatMessage> findByConversationIdAndStatusOrderByCreatedAtDescIdDesc(UUID conversationId,
      ChatMessageStatus status, Pageable pageable);

  @Modifying
  @Query("""
      update ChatMessage m
      set m.content = :content, m.status = 'COMPLETED', m.pendingSince = null, m.promptTokens = :promptTokens,
          m.completionTokens = :completionTokens, m.modelVersion = :modelVersion,
          m.latencyMs = :latencyMs, m.metadata = :metadata
      where m.id = :id and m.status = 'PENDING'
      """)
  int completePending(@Param("id") UUID id, @Param("content") String content,
      @Param("promptTokens") Integer promptTokens, @Param("completionTokens") Integer completionTokens,
      @Param("modelVersion") String modelVersion, @Param("latencyMs") Integer latencyMs,
      @Param("metadata") java.util.Map<String, Object> metadata);

  @Modifying
  @Query("update ChatMessage m set m.status = 'FAILED', m.pendingSince = null, m.metadata = :metadata where m.id = :id and m.status = 'PENDING'")
  int failPending(@Param("id") UUID id, @Param("metadata") java.util.Map<String, Object> metadata);

  @Modifying
  @Query("update ChatMessage m set m.status = 'FAILED', m.pendingSince = null, m.metadata = :metadata "
      + "where m.status = 'PENDING' and (m.pendingSince < :cutoff or (m.pendingSince is null and m.createdAt < :cutoff))")
  int failExpiredPending(@Param("cutoff") Instant cutoff,
      @Param("metadata") java.util.Map<String, Object> metadata);

  @Modifying
  @Query("update ChatMessage m set m.status = 'PENDING', m.pendingSince = :pendingSince, m.metadata = null "
      + "where m.id = :id and m.status = 'FAILED'")
  int retryFailed(@Param("id") UUID id, @Param("pendingSince") Instant pendingSince);

  @Query("""
      select m from ChatMessage m
      where m.conversation.id = :conversationId
        and (m.createdAt < :beforeCreatedAt
             or (m.createdAt = :beforeCreatedAt and m.id < :beforeId))
      order by m.createdAt desc, m.id desc
      """)
  List<ChatMessage> findBefore(@Param("conversationId") UUID conversationId,
      @Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeId") UUID beforeId,
      Pageable pageable);

  List<ChatMessage> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

  @Modifying
  @Query("delete from ChatMessage m where m.conversation.id in (select c.id from Conversation c where c.deletedAt is not null and c.deletedAt < :cutoff)")
  int deleteSoftDeletedBefore(@Param("cutoff") Instant cutoff);
}
