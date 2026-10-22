package io.cvvexxx.billings.payment.service;


import io.cvvexxx.billings.account.entity.Account;
import io.cvvexxx.billings.account.entity.AccountStatus;
import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.repository.AccountRepository;
import io.cvvexxx.billings.payment.dto.PaymentDto;
import io.cvvexxx.billings.payment.entity.Payment;
import io.cvvexxx.billings.payment.exception.IdempotencyKeyReusedException;
import io.cvvexxx.billings.payment.exception.PaymentNotFoundException;
import io.cvvexxx.billings.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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

    @Transactional
    public PaymentDto createPayment(
            UUID userId, BigDecimal amount, String idempotencyKey
    ) {
        Account account = findAccount(userId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException("Account with id %s is closed".formatted(account.getId()));
        }

        paymentRepository.insertIfAbsent(account.getId(), amount, RUB_CURRENCY, STUB_PROVIDER, idempotencyKey);

        Payment payment = paymentRepository.findByAccountIdAndIdempotencyKey(account.getId(), idempotencyKey)
                .orElseThrow(
                        () -> new PaymentNotFoundException("Payment with account id %s and idempotencyKey %s not found"
                                .formatted(account.getId(), idempotencyKey)
                        )
                );

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new IdempotencyKeyReusedException("Idempotency key %s already used with amount %s"
                    .formatted(idempotencyKey, payment.getAmount()));
        }

        return toDto(payment);
    }

    @Transactional(readOnly = true)
    public PaymentDto getMyPayment(UUID userId, UUID paymentId) {
        Account account = findAccount(userId);
        return toDto(paymentRepository.findByIdAndAccountId(paymentId, account.getId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment with id %s not found".formatted(paymentId))));
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto> getMyPayments(UUID userId, Pageable pageable) {
        UUID accountId = findAccount(userId).getId();

        Page<Payment> payments = paymentRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        return payments.map(this::toDto);
    }

    private Account findAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Account with userId %s not found".formatted(userId)));
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
