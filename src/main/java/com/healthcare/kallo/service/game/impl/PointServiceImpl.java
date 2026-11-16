package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.model.game.enums.LeaderboardMetric;
import com.healthcare.kallo.model.game.enums.PointSource;
import com.healthcare.kallo.repository.game.PointTransactionRepository;
import com.healthcare.kallo.service.game.LeaderboardChangeNotifier;
import com.healthcare.kallo.service.game.PointService;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointServiceImpl implements PointService {
  private final PointTransactionRepository transactions;
  private final LeaderboardChangeNotifier leaderboard;

  public PointServiceImpl(PointTransactionRepository transactions, LeaderboardChangeNotifier leaderboard) {
    this.transactions = transactions;
    this.leaderboard = leaderboard;
  }

  @Override
  @Transactional
  public boolean award(UUID userId, PointSource source, UUID sourceId, int points) {
    if (points <= 0) return false;
    boolean inserted = transactions.insertIfAbsent(userId, points, source.name(), sourceId) == 1;
    if (inserted) afterCommit(() -> leaderboard.changed(userId, LeaderboardMetric.POINTS));
    return inserted;
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        action.run();
      }
    });
  }
}
