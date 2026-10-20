package io.cvvexxx.billings.account.service;

import io.cvvexxx.billings.account.dto.AccountDto;
import io.cvvexxx.billings.account.dto.OpenAccountResult;
import io.cvvexxx.billings.account.entity.Account;
import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.repository.AccountRepository;
import io.cvvexxx.billings.payment.entity.Payment;
import io.cvvexxx.billings.transaction.dto.TransactionDto;
import io.cvvexxx.billings.transaction.entity.Transaction;
import io.cvvexxx.billings.transaction.entity.TransactionType;
import io.cvvexxx.billings.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        accountService = new AccountService(accountRepository, transactionRepository);
    }

    @Test
    @DisplayName("openAccount: вставка прошла — created=true, в ответе свежий счёт")
    void openAccount_whenInserted_returnsCreated() {
        Account account = account();
        when(accountRepository.insertIfAbsent(USER_ID)).thenReturn(1);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        OpenAccountResult result = accountService.openAccount(USER_ID);

        assertThat(result.created()).isTrue();
        assertThat(result.account()).isEqualTo(new AccountDto(
                account.getId(), "1000000001", BigDecimal.ZERO, "RUB", AccountStatus.ACTIVE));
    }

    @Test
    @DisplayName("openAccount: счёт уже был — created=false, возвращается существующий")
    void openAccount_whenAlreadyExists_returnsExisting() {
        Account account = account();
        account.setBalance(new BigDecimal("150.00"));
        when(accountRepository.insertIfAbsent(USER_ID)).thenReturn(0);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        OpenAccountResult result = accountService.openAccount(USER_ID);

        assertThat(result.created()).isFalse();
        assertThat(result.account().id()).isEqualTo(account.getId());
        assertThat(result.account().balance()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("getMyAccount: маппит сущность в DTO")
    void getMyAccount_mapsEntityToDto() {
        Account account = account();
        account.setStatus(AccountStatus.BLOCKED);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        AccountDto dto = accountService.getMyAccount(USER_ID);

        assertThat(dto.id()).isEqualTo(account.getId());
        assertThat(dto.number()).isEqualTo("1000000001");
        assertThat(dto.status()).isEqualTo(AccountStatus.BLOCKED);
    }

    @Test
    @DisplayName("getMyAccount: счёта нет — AccountNotFoundException с userId в сообщении")
    void getMyAccount_whenMissing_throws() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getMyAccount(USER_ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());
    }

    @Test
    @DisplayName("getMyTransactions: ищет по id счёта и маппит paymentId, если платёж есть")
    void getMyTransactions_mapsTransactionsOfOwnAccount() {
        Account account = account();
        Payment payment = Payment.builder().id(UUID.randomUUID()).build();
        Transaction topUp = transaction(account, TransactionType.TOP_UP, "500.00", "500.00", payment);
        Transaction charge = transaction(account, TransactionType.CHARGE, "100.00", "400.00", null);
        Pageable pageable = PageRequest.of(0, 20);

        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(charge, topUp), pageable, 2));

        Page<TransactionDto> page = accountService.getMyTransactions(USER_ID, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(TransactionDto::type)
                .containsExactly(TransactionType.CHARGE, TransactionType.TOP_UP);
        assertThat(page.getContent().get(0).paymentId()).isNull();
        assertThat(page.getContent().get(1).paymentId()).isEqualTo(payment.getId());
        assertThat(page.getContent().get(1).balanceAfter()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("getMyTransactions: счёта нет — исключение, в репозиторий транзакций не ходим")
    void getMyTransactions_whenAccountMissing_throws() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getMyTransactions(USER_ID, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotFoundException.class);
        verify(transactionRepository, never()).findByAccountIdOrderByCreatedAtDesc(any(), any());
    }

    private Account account() {
        return Account.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .number("1000000001")
                .build();
    }

    private Transaction transaction(Account account, TransactionType type, String amount, String balanceAfter,
                                    Payment payment) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .account(account)
                .type(type)
                .amount(new BigDecimal(amount))
                .balanceAfter(new BigDecimal(balanceAfter))
                .payment(payment)
                .createdAt(Instant.now())
                .build();
    }
}
