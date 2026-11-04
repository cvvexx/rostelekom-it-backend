package io.cvvexxx.services.subscription.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubscriptionDto(
        @NotNull(message = "{validation.subscription-create.tariff-id.required}")
        UUID tariffId
) {
}
