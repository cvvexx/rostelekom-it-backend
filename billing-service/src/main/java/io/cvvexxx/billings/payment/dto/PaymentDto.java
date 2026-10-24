package io.cvvexxx.billings.payment.dto;

import io.cvvexxx.billings.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String confirmationUrl,
        Instant createdAt
) {
}
