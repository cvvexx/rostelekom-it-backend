package io.cvvexxx.billings.charges.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateChargeDto(

        @NotNull(message = "{validation.charge-create.user-id.required}")
        UUID userId,

        @NotNull(message = "{validation.charge-create.amount.required}")
        @Positive(message = "{validation.charge-create.amount.positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.charge-create.amount.digits}")//Не больше 2 знаков после запятой, как в бд
        BigDecimal amount,

        //NotBlank, а не NotNull: с пустым или null ключом уникальный индекс не сработает и ретрай спишет второй раз
        @NotBlank(message = "{validation.charge-create.idempotency-key.required}")
        @Size(max = 64, message = "{validation.charge-create.idempotency-key.too-long}")
        String idempotencyKey,

        @Size(max = 255, message = "{validation.charge-create.description.too-long}")
        String description
) {
}
