package io.cvvexxx.billings.account.dto;

import io.cvvexxx.billings.account.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusDto(
        @NotNull
        AccountStatus accountStatus
) {
}
