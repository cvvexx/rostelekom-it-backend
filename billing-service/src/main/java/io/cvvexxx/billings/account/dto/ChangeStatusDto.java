package io.cvvexxx.billings.account.dto;

import io.cvvexxx.billings.account.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusDto(
        @NotNull(message = "{validation.account-status-change.account-status.required}")
        AccountStatus accountStatus
) {
}
