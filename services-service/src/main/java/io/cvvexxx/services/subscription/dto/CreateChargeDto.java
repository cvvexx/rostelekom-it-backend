package io.cvvexxx.services.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateChargeDto(
        UUID userId,
        BigDecimal amount,
        String idempotencyKey,
        String description
) {
}
