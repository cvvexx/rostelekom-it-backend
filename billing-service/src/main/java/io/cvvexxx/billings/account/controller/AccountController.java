package io.cvvexxx.billings.account.controller;


import io.cvvexxx.billings.account.dto.AccountDto;
import io.cvvexxx.billings.account.dto.ChangeStatusDto;
import io.cvvexxx.billings.account.dto.OpenAccountResult;
import io.cvvexxx.billings.account.service.AccountService;
import io.cvvexxx.billings.transaction.dto.TransactionDto;
import jakarta.validation.Valid;
import io.cvvexxx.billings.advice.InvalidSortException;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

//Контроллер лицевых счетов
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing/accounts")
public class AccountController {

    //Поля, по которым фронт может сортировать историю операций
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "amount", "type");

    private final AccountService accountService;

    //Открывает счёт текущему юзеру: 201 если создали, 200 если уже был
    @PostMapping
    public ResponseEntity<AccountDto> openAccount(@AuthenticationPrincipal Jwt jwt) {
        OpenAccountResult result = accountService.openAccount(currentUserId(jwt));
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.account());
    }

    @GetMapping("/me")
    public ResponseEntity<AccountDto> getMyAccount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(accountService.getMyAccount(currentUserId(jwt)));
    }

    //История операций по счёту, новые сверху
    @GetMapping("/me/transactions")
    public ResponseEntity<PagedModel<TransactionDto>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        InvalidSortException.requireAllowed(pageable, SORT_FIELDS);
        return ResponseEntity.ok(new PagedModel<>(accountService.getMyTransactions(currentUserId(jwt), pageable)));
    }

    //Только для админа: любой счёт по id
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountDto> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    //Только для админа: заблокировать/разблокировать/закрыть счёт
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountDto> changeStatus(
            @PathVariable("id") UUID id,
            @RequestBody @Valid ChangeStatusDto changeStatusDto
    ) {
        return ResponseEntity.ok(accountService.changeStatus(id, changeStatusDto.accountStatus()));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());//sub в токене - id юзера из user-service
    }
}
