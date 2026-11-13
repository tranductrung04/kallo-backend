package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
  @Query("""
      select f from Friendship f
      where (f.requester.id = :first and f.addressee.id = :second)
         or (f.requester.id = :second and f.addressee.id = :first)
      """)
  Optional<Friendship> findBetween(@Param("first") UUID first, @Param("second") UUID second);

  @Query("""
      select f from Friendship f
      where f.status = :status and (f.requester.id = :userId or f.addressee.id = :userId)
      order by f.respondedAt desc nulls last, f.id desc
      """)
  List<Friendship> findByUserAndStatus(@Param("userId") UUID userId,
      @Param("status") FriendshipStatus status, Pageable pageable);

  List<Friendship> findByAddresseeIdAndStatusOrderByCreatedAtDesc(UUID userId, FriendshipStatus status);

  List<Friendship> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID userId, FriendshipStatus status);

  @Query("""
      select case when f.requester.id = :userId then f.addressee.id else f.requester.id end
      from Friendship f
      where f.status = com.healthcare.kallo.model.game.enums.FriendshipStatus.ACCEPTED
        and (f.requester.id = :userId or f.addressee.id = :userId)
      """)
  List<UUID> findAcceptedFriendIds(@Param("userId") UUID userId);

  @Query("""
      select count(f) from Friendship f
      where f.status = com.healthcare.kallo.model.game.enums.FriendshipStatus.ACCEPTED
        and (f.requester.id = :userId or f.addressee.id = :userId)
      """)
  long countAcceptedByUserId(@Param("userId") UUID userId);

  long countByRequesterIdAndStatus(UUID userId, FriendshipStatus status);
}
