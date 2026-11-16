package com.healthcare.kallo.event.game;

import java.util.UUID;

public record FriendRequestReceivedEvent(UUID recipientId, UUID requestId, UUID requesterId) {}
