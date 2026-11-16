package io.cvvexxx.billings.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ToppedUpEvent(
        UUID userId,
        UUID accountId,
        BigDecimal amount,
        Instant occurredAt
) {
}
