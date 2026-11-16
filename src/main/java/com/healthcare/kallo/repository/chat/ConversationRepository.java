package com.healthcare.kallo.repository.chat;

import com.healthcare.kallo.model.chat.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  Optional<Conversation> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  Optional<Conversation> findByUserIdAndInitialClientMessageIdAndDeletedAtIsNull(UUID userId, UUID clientMessageId);

  @Query("""
      select c from Conversation c
      where c.user.id = :userId and c.deletedAt is null
      order by case when c.lastMessageAt is null then 1 else 0 end,
               c.lastMessageAt desc, c.id desc
      """)
  Page<Conversation> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

  @Modifying
  @Query("""
      update Conversation c
      set c.messageCount = c.messageCount + 2,
          c.lastMessageAt = :lastMessageAt,
          c.title = case when c.title is null then :title else c.title end,
          c.updatedAt = CURRENT_TIMESTAMP
      where c.id = :id and c.deletedAt is null
      """)
  int addMessages(@Param("id") UUID id, @Param("lastMessageAt") java.time.Instant lastMessageAt,
      @Param("title") String title);

  @Modifying
  @Query("""
      update Conversation c
      set c.lastMessageAt = :lastMessageAt, c.updatedAt = CURRENT_TIMESTAMP
      where c.id = :id and c.deletedAt is null
      """)
  int touchLastMessageAt(@Param("id") UUID id, @Param("lastMessageAt") java.time.Instant lastMessageAt);
}
