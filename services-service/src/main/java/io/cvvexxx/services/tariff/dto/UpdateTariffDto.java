package io.cvvexxx.services.tariff.dto;

import io.cvvexxx.services.tariff.entity.TariffCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTariffDto(
        @Size(min = 1, max = 100, message = "{validation.tariff.name.size}")
        String name,

        @Size(max = 500, message = "{validation.tariff.description.too-long}")
        String description,

        TariffCategory category,

        @Positive(message = "{validation.tariff.price.positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.tariff.price.digits}")
        BigDecimal price,

        Boolean active
) {
}