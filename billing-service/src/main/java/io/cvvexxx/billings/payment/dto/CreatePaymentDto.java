package io.cvvexxx.billings.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentDto(
        @NotNull(message = "{validation.payment-create.amount.required}")
        @Digits(integer = 17, fraction = 2, message = "{validation.payment-create.amount.digits}")//Не больше 2 знаков после запятой, как в бд
        @DecimalMin(value = "1.00", message = "{validation.payment-create.amount.less-than-min}")
        @DecimalMax(value = "100000.00", message = "{validation.payment-create.amount.more-than-max}")
        BigDecimal amount
) {
}
