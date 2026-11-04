package io.cvvexxx.services.subscription.dto;

import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionDto(
        UUID id,
        UUID userId,
        UUID tariffId,
        String TariffName,
        TariffCategory tariffCategory,
        SubscriptionStatus status,
        BigDecimal price,
        String currency,
        BillingPeriod billingPeriod,
        Instant currentPeriodStart,
        Instant nextChargeAt,
        boolean cancelAtPeriodEnd,
        Instant canceledAt,
        UUID lastChargeTransactionalId,
        Instant createdAt
) {
}
