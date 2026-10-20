package io.cvvexxx.billings.account.service;


import io.cvvexxx.billings.account.dto.AccountDto;
import io.cvvexxx.billings.account.dto.OpenAccountResult;
import io.cvvexxx.billings.account.entity.Account;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.repository.AccountRepository;
import io.cvvexxx.billings.transaction.dto.TransactionDto;
import io.cvvexxx.billings.transaction.entity.Transaction;
import io.cvvexxx.billings.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public OpenAccountResult openAccount(UUID userId) {
        boolean created = accountRepository.insertIfAbsent(userId) == 1;

        return new OpenAccountResult(toDto(findAccount(userId)), created);
    }


    @Transactional(readOnly = true)
    public AccountDto getMyAccount(UUID userId) {
        return toDto(findAccount(userId));
    }

    @Transactional(readOnly = true)
    public Page<TransactionDto> getMyTransactions(UUID userId, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(findAccount(userId).getId(), pageable);

        return transactions.map(this::toDto);
    }

    private Account findAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account with userId %s not found".formatted(userId)));
    }

    private TransactionDto toDto(Transaction transaction) {
        return new TransactionDto(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getPayment() != null ? transaction.getPayment().getId() : null,
                transaction.getCreatedAt()
        );
    }

    private AccountDto toDto(Account account) {
        return new AccountDto(
                account.getId(),
                account.getNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus()
        );

    }
}
