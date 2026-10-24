package io.cvvexxx.billings.transaction.dto;

import io.cvvexxx.billings.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionDto(
    UUID id,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String description,
    UUID paymentId,
    Instant createdAt
) {
}
