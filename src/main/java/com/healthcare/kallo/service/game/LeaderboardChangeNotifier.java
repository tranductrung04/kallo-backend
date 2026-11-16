package com.healthcare.kallo.service.game;

import com.healthcare.kallo.config.GameProperties;
import com.healthcare.kallo.model.game.UserGameSettings;
import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import com.healthcare.kallo.repository.game.FriendshipRepository;
import com.healthcare.kallo.repository.game.UserGameSettingsRepository;
import com.healthcare.kallo.service.sys.RealtimePublisher;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
public class LeaderboardChangeNotifier implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(LeaderboardChangeNotifier.class);

  private final LeaderboardService leaderboard;
  private final FriendshipRepository friendships;
  private final UserGameSettingsRepository settings;
  private final RealtimePublisher realtime;
  private final SimpUserRegistry onlineUsers;
  private final Duration debounce;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
      runnable -> {
        Thread thread = new Thread(runnable, "kallo-game-leaderboard");
        thread.setDaemon(true);
        return thread;
      });
  private final Map<Key, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
  private volatile boolean running;

  public LeaderboardChangeNotifier(
      LeaderboardService leaderboard,
      FriendshipRepository friendships,
      UserGameSettingsRepository settings,
      RealtimePublisher realtime,
      SimpUserRegistry onlineUsers,
      GameProperties properties) {
    this.leaderboard = leaderboard;
    this.friendships = friendships;
    this.settings = settings;
    this.realtime = realtime;
    this.onlineUsers = onlineUsers;
    this.debounce = properties.leaderboard().pushDebounce();
  }

  public void changed(UUID userId, LeaderboardMetric metric) {
    if (!running || userId == null || metric == null) return;
    Key key = new Key(userId, metric);
    try {
      ScheduledFuture<?> previous = pending.put(key, executor.schedule(
          () -> publish(key), debounce.toMillis(), TimeUnit.MILLISECONDS));
      if (previous != null) previous.cancel(false);
    } catch (java.util.concurrent.RejectedExecutionException ignored) {
      log.debug("Leaderboard update skipped while notifier is stopping");
    }
  }

  private void publish(Key key) {
    pending.remove(key);
    try {
      UserGameSettings ownerSettings = settings.findById(key.userId()).orElse(null);
      if (!shares(ownerSettings, key.metric())) return;
      long value = leaderboard.valueOf(key.userId(), key.metric());
      Set<UUID> recipients = new java.util.HashSet<>(friendships.findAcceptedFriendIds(key.userId()));
      recipients.add(key.userId());
      for (UUID recipient : recipients) {
        if (onlineUsers.getUser(recipient.toString()) == null) continue;
        realtime.sendToUser(recipient, "/queue/leaderboard", "LEADERBOARD_UPDATE",
            new LeaderboardUpdate(key.metric(), key.userId(), value));
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to publish leaderboard update for user {}", key.userId(), exception);
    }
  }

  private boolean shares(UserGameSettings settings, LeaderboardMetric metric) {
    if (settings == null) return metric != LeaderboardMetric.STEPS;
    return switch (metric) {
      case STREAK -> settings.isShareStreak();
      case POINTS -> settings.isSharePoints();
      case STEPS -> settings.isShareSteps();
    };
  }

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    pending.values().forEach(future -> future.cancel(false));
    pending.clear();
    executor.shutdownNow();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  public record LeaderboardUpdate(LeaderboardMetric metric, UUID userId, long value) {}

  private record Key(UUID userId, LeaderboardMetric metric) {}
}
