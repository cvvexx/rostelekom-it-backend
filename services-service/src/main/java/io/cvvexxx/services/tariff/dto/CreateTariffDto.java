package io.cvvexxx.services.tariff.dto;

import io.cvvexxx.services.tariff.entity.BillingPeriod;
import io.cvvexxx.services.tariff.entity.TariffCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTariffDto(
        @NotBlank(message = "{validation.tariff-create.name.required}")
        @Size(max = 100, message = "{validation.tariff-create.name.too-long}")
        String name,

        @Size(max = 500, message = "{validation.tariff-create.description.too-long}")
        String description,

        @NotNull(message = "{validation.tariff-create.category.required}")
        TariffCategory category,

        @NotNull(message = "{validation.tariff-create.price.required}")
        @Positive(message = "{validation.tariff-create.price.positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.tariff-create.price.digits}")
        BigDecimal price,

        BillingPeriod billingPeriod
) {
}