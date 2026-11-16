package io.cvvexxx.services.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ToppedUpEvent (
        UUID userId,
        UUID accountId,
        BigDecimal amount,
        Instant occuradAt
){
}
