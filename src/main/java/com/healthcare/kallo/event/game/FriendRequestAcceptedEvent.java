package com.healthcare.kallo.event.game;

import java.util.UUID;

public record FriendRequestAcceptedEvent(UUID recipientId, UUID friendId) {}
