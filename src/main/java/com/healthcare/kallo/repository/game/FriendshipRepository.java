package com.healthcare.kallo.repository.game;

import com.healthcare.kallo.model.game.Friendship;
import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
  @Query("""
      select f from Friendship f
      where (f.requester.id = :first and f.addressee.id = :second)
         or (f.requester.id = :second and f.addressee.id = :first)
      """)
  Optional<Friendship> findBetween(@Param("first") UUID first, @Param("second") UUID second);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from Friendship f where (f.requester.id = :first and f.addressee.id = :second) or (f.requester.id = :second and f.addressee.id = :first)")
  Optional<Friendship> findBetweenForUpdate(@Param("first") UUID first, @Param("second") UUID second);

  @Query("""
      select f from Friendship f
      join fetch f.requester join fetch f.addressee
      where f.status = :status and (f.requester.id = :userId or f.addressee.id = :userId)
      order by f.respondedAt desc nulls last, f.id desc
      """)
  Slice<Friendship> findByUserAndStatus(@Param("userId") UUID userId,
      @Param("status") FriendshipStatus status, Pageable pageable);

  @Query("select f from Friendship f join fetch f.requester join fetch f.addressee where f.addressee.id = :userId and f.status = :status order by f.createdAt desc")
  List<Friendship> findByAddresseeIdAndStatusOrderByCreatedAtDesc(UUID userId, FriendshipStatus status);

  @Query("select f from Friendship f join fetch f.requester join fetch f.addressee where f.requester.id = :userId and f.status = :status order by f.createdAt desc")
  List<Friendship> findByRequesterIdAndStatusOrderByCreatedAtDesc(UUID userId, FriendshipStatus status);

  @Query("select f from Friendship f join fetch f.requester join fetch f.addressee where f.requester.id = :userId and f.addressee.id in :otherIds or f.addressee.id = :userId and f.requester.id in :otherIds")
  List<Friendship> findBetweenAny(@Param("userId") UUID userId, @Param("otherIds") Collection<UUID> otherIds);

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

  long countByAddresseeIdAndStatus(UUID userId, FriendshipStatus status);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("delete from Friendship f where f.status = com.healthcare.kallo.model.game.enums.FriendshipStatus.ACCEPTED and ((f.requester.id = :firstRequester and f.addressee.id = :firstAddressee) or (f.requester.id = :secondRequester and f.addressee.id = :secondAddressee))")
  int deleteAcceptedPair(@Param("firstRequester") UUID firstRequester, @Param("firstAddressee") UUID firstAddressee,
      @Param("secondRequester") UUID secondRequester, @Param("secondAddressee") UUID secondAddressee);
}
