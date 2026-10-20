package io.cvvexxx.billings.account.dto;

public record OpenAccountResult(
        AccountDto account,
        boolean created
) {
}
