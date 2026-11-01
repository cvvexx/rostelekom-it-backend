package io.cvvexxx.billings.charges.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargeDto(
        UUID transactionId,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}
