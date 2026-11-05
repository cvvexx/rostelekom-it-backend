package io.cvvexxx.services.tariff.dto;

import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TariffDto(
        UUID id,
        String name,
        String description,
        TariffCategory category,
        BigDecimal price,
        String currency,
        BillingPeriod billingPeriod,
        boolean active,
        Instant createdAt
) {
}
