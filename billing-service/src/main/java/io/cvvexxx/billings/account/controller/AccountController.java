package io.cvvexxx.billings.account.controller;


import io.cvvexxx.billings.account.dto.AccountDto;
import io.cvvexxx.billings.account.dto.ChangeStatusDto;
import io.cvvexxx.billings.account.dto.OpenAccountResult;
import io.cvvexxx.billings.account.service.AccountService;
import io.cvvexxx.billings.transaction.dto.TransactionDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing/accounts")
public class AccountController {

    private final AccountService accountService;

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

    @GetMapping("/me/transactions")
    public ResponseEntity<PagedModel<TransactionDto>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(accountService.getMyTransactions(currentUserId(jwt), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountDto> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountDto> changeStatus(
            @PathVariable("id") UUID id,
            @RequestBody @Valid ChangeStatusDto changeStatusDto
    ) {
        return ResponseEntity.ok(accountService.changeStatus(id, changeStatusDto.accountStatus()));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
