package io.cvvexxx.billings.account.dto;

import io.cvvexxx.billings.account.entity.AccountStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountDto(
        UUID id,
        String number,
        BigDecimal balance,
        String currency,
        AccountStatus status
) {
}
