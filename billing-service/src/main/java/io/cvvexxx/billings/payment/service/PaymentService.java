package io.cvvexxx.billings.payment.service;


import io.cvvexxx.billings.account.entity.Account;
import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.repository.AccountRepository;
import io.cvvexxx.billings.payment.dto.PaymentDto;
import io.cvvexxx.billings.payment.entity.Payment;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.payment.exception.IdempotencyKeyReusedException;
import io.cvvexxx.billings.payment.exception.PaymentNotFoundException;
import io.cvvexxx.billings.payment.exception.PaymentStatusConflictException;
import io.cvvexxx.billings.payment.repository.PaymentRepository;
import io.cvvexxx.billings.transaction.entity.Transaction;
import io.cvvexxx.billings.transaction.entity.TransactionType;
import io.cvvexxx.billings.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final static String RUB_CURRENCY = "RUB";
    private final static String STUB_PROVIDER = "STUB";

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public PaymentDto createPayment(
            UUID userId, BigDecimal amount, String idempotencyKey
    ) {
        Account account = findAccount(userId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException("account.closed", account.getId());
        }

        paymentRepository.insertIfAbsent(account.getId(), amount, RUB_CURRENCY, STUB_PROVIDER, idempotencyKey);

        Payment payment = paymentRepository.findByAccountIdAndIdempotencyKey(account.getId(), idempotencyKey)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "payment.not-found-by-idempotency-key", account.getId(), idempotencyKey));

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new IdempotencyKeyReusedException("payment.idempotency-key-reused", idempotencyKey, payment.getAmount().toPlainString());
        }

        return toDto(payment);
    }

    @Transactional(readOnly = true)
    public PaymentDto getMyPayment(UUID userId, UUID paymentId) {
        Account account = findAccount(userId);
        return toDto(paymentRepository.findByIdAndAccountId(paymentId, account.getId())
                .orElseThrow(() -> new PaymentNotFoundException("payment.not-found", paymentId)));
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto> getMyPayments(UUID userId, Pageable pageable) {
        UUID accountId = findAccount(userId).getId();

        Page<Payment> payments = paymentRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        return payments.map(this::toDto);
    }

    @Transactional
    public PaymentDto confirmPayment(UUID userId, UUID paymentId, boolean isAdmin) {
        Payment payment = findPayment(userId, paymentId, isAdmin);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {//Повторное подтверждение — просто отдаём платёж
            return toDto(payment);
        }
        if (payment.getStatus() == PaymentStatus.CANCELED) {//Отменённый платёж подтвердить нельзя
            throw new PaymentStatusConflictException("payment.status-conflict", paymentId, payment.getStatus());
        }
        Account account = accountRepository.findByIdForUpdate(payment.getAccount().getId())
                .orElseThrow(() -> new AccountNotFoundException("account.not-found", payment.getAccount().getId()));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException("account.closed", account.getId());
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);//Выставляем статус на SUCCEEDED

        BigDecimal paymentAmount = payment.getAmount();

        account.setBalance(account.getBalance().add(paymentAmount));//добавляем сумму на баланс

        Transaction transaction = Transaction.builder()//Создаем транзакцию для истории операций
                .account(account)
                .type(TransactionType.TOP_UP)
                .amount(paymentAmount)
                .payment(payment)
                .balanceAfter(account.getBalance())
                .build();
        transactionRepository.save(transaction);//сохраняем транзакцию

        return toDto(payment);
    }

    @Transactional
    public PaymentDto cancelPayment(UUID userId, UUID paymentId, boolean isAdmin) {
        Payment payment = findPayment(userId, paymentId, isAdmin);

        if (payment.getStatus() == PaymentStatus.CANCELED) {//Повторная отмена — просто отдаём платёж
            return toDto(payment);
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {//Оплаченный платёж отменить нельзя
            throw new PaymentStatusConflictException("payment.status-conflict", paymentId, payment.getStatus());
        }

        payment.setStatus(PaymentStatus.CANCELED);

        return toDto(payment);
    }

    private Payment findPayment(UUID userId, UUID paymentId, boolean isAdmin) {
        return (
                isAdmin
                        ? paymentRepository.findByIdForUpdate(paymentId)//Если админ, то получаем платеж без айди аккаунта
                        : paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId)
        ).orElseThrow(() -> new PaymentNotFoundException("payment.not-found", paymentId));
    }

    private Account findAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("account.not-found-by-user", userId));
    }

    private PaymentDto toDto(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getConfirmationUrl(),
                payment.getCreatedAt()
        );
    }
}
