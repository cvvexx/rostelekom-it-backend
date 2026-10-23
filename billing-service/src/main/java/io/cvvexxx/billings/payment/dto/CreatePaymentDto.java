package io.cvvexxx.billings.payment.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePaymentDto(
        @NotNull(message = "{validation.payment-create.amount.required}")
        @Positive(message = "{validation.payment-create.amount.positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.payment-create.amount.digits}")
        BigDecimal amount
) {
}
