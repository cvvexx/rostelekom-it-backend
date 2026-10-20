package io.cvvexxx.billings.account.controller;


import io.cvvexxx.billings.account.dto.AccountDto;
import io.cvvexxx.billings.account.dto.OpenAccountResult;
import io.cvvexxx.billings.account.service.AccountService;
import io.cvvexxx.billings.transaction.dto.TransactionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
