package io.cvvexxx.billings.account.dto;

//created нужен контроллеру, чтобы выбрать 201 или 200
public record OpenAccountResult(
        AccountDto account,
        boolean created
) {
}
