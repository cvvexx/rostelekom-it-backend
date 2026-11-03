package io.cvvexxx.services.tariff.dto;

import io.cvvexxx.services.tariff.entity.TariffCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTariffDto(
        @Pattern(regexp = ".*\\S.*", message = "{validation.tariff-update.name.not-blank}")
        @Size(max = 100, message = "{validation.tariff-update.name.too-long}")
        String name,

        @Size(max = 500, message = "{validation.tariff-update.description.too-long}")
        String description,

        TariffCategory category,

        @Positive(message = "{validation.tariff-update.price.positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.tariff-update.price.digits}")
        BigDecimal price,

        Boolean active
) {
}