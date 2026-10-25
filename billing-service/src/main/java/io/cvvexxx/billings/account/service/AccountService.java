package io.cvvexxx.billings.account.service;


import io.cvvexxx.billings.account.dto.AccountDto;
import io.cvvexxx.billings.account.dto.OpenAccountResult;
import io.cvvexxx.billings.account.entity.Account;
import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountHasPendingPaymentsException;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.exception.AccountStatusConflictException;
import io.cvvexxx.billings.account.repository.AccountRepository;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.payment.repository.PaymentRepository;
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
    private final PaymentRepository paymentRepository;

    //Создаём счёт, если его ещё нет, и запоминаем, создали ли новый
    @Transactional
    public OpenAccountResult openAccount(UUID userId) {
        boolean created = accountRepository.insertIfAbsent(userId) == 1;

        return new OpenAccountResult(toDto(findAccount(userId)), created);
    }

    //Счёт открывается при первом запросе, чтобы фронту не нужно было отдельно вызывать POST
    @Transactional
    public AccountDto getMyAccount(UUID userId) {
        return openAccount(userId).account();
    }

    @Transactional(readOnly = true)
    public Page<TransactionDto> getMyTransactions(UUID userId, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository
                .findByAccountId(findAccount(userId).getId(), pageable);

        return transactions.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(UUID id) {
        return toDto(accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("account.not-found", id)));
    }

    @Transactional
    public AccountDto changeStatus(UUID id, AccountStatus accountStatus) {
        Account account = findAccountByIdForUpdate(id);//С блокировкой строки, чтобы два админа не перетёрли друг друга
        AccountStatus dbAccountStatus = account.getStatus();

        //Закрытый счёт больше не меняем
        if (dbAccountStatus == AccountStatus.CLOSED) {
            throw new AccountClosedException("account.already-closed", account.getId());
        }

        if (dbAccountStatus == accountStatus) {
            throw new AccountStatusConflictException("account.status-already-set", dbAccountStatus, account.getId());
        }

        if (accountStatus == AccountStatus.CLOSED
                && paymentRepository.existsByStatusAndAccountId(PaymentStatus.PENDING, account.getId())) {
            throw new AccountHasPendingPaymentsException("account.has-pending-payments", account.getId());
        }

        account.setStatus(accountStatus);//save не нужен, Hibernate сам сохранит изменения при коммите

        return toDto(account);
    }

    private Account findAccountByIdForUpdate(UUID id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException("account.not-found", id));
    }

    private Account findAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("account.not-found-by-user", userId));
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
