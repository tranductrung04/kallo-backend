package com.healthcare.kallo.service.game.impl;

import com.healthcare.kallo.model.game.enums.PointSource;
import com.healthcare.kallo.repository.game.PointTransactionRepository;
import com.healthcare.kallo.service.game.PointService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointServiceImpl implements PointService {
  private final PointTransactionRepository transactions;

  public PointServiceImpl(PointTransactionRepository transactions) {
    this.transactions = transactions;
  }

  @Override
  @Transactional
  public boolean award(UUID userId, PointSource source, UUID sourceId, int points) {
    if (points <= 0) return false;
    return transactions.insertIfAbsent(userId, points, source.name(), sourceId) == 1;
  }
}
