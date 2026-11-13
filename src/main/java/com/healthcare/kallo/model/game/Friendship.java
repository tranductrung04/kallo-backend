package com.healthcare.kallo.model.game;

import com.healthcare.kallo.model.game.enums.FriendshipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.healthcare.kallo.model.auth.User;

@Entity
@Table(name = "friendships")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Friendship {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requester_id", nullable = false)
  private User requester;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "addressee_id", nullable = false)
  private User addressee;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FriendshipStatus status = FriendshipStatus.PENDING;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  private Instant respondedAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof Friendship that && id != null && id.equals(that.id));
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
