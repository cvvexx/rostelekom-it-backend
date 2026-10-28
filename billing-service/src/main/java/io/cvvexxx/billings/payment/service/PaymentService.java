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
import io.cvvexxx.billings.payment.provider.PaymentProvider;
import io.cvvexxx.billings.payment.provider.ProviderPayment;
import io.cvvexxx.billings.payment.provider.ProviderStatus;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final static String RUB_CURRENCY = "RUB";

    private final TransactionTemplate transactionTemplate;
    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentProvider paymentProvider;

    //Без @Transactional: провайдер вызывается между двумя короткими транзакциями
    public PaymentDto createPayment(
            UUID userId, BigDecimal amount, String idempotencyKey
    ) {
        //1. Коммитим платёж до похода к провайдеру: повтор с тем же ключом найдёт этот же paymentId,
        //и идемпотентный ответ провайдера привяжется к нему. Блокировки отпускаются сразу после коммита
        Payment payment = transactionTemplate.execute(status -> {
            Account account = findAccountByUserIdForUpdate(userId);//С блокировкой счёта, чтобы платёж не создался параллельно с его закрытием

            if (account.getStatus() == AccountStatus.CLOSED) {
                throw new AccountClosedException("account.closed", account.getId());
            }

            //Если платёж с таким ключом уже есть, вставка ничего не сделает и ниже прочитаем существующий
            paymentRepository.insertIfAbsent(account.getId(), amount, RUB_CURRENCY, paymentProvider.name(), idempotencyKey);

            Payment existing = paymentRepository.findByAccountIdAndIdempotencyKey(account.getId(), idempotencyKey)
                    .orElseThrow(() -> new PaymentNotFoundException(
                            "payment.not-found-by-idempotency-key", account.getId(), idempotencyKey));

            //Ключ тот же, а сумма другая - значит это другой запрос, отклоняем
            if (existing.getAmount().compareTo(amount) != 0) {
                throw new IdempotencyKeyReusedException(
                        "payment.idempotency-key-reused", idempotencyKey, existing.getAmount().toPlainString()
                );
            }
            return existing;
        });

        if (payment.getProviderPaymentId() != null) {//Повтор запроса - ссылка уже есть
            return toDto(payment);
        }

        //2. Провайдер вне транзакции. Если упадёт - платёж останется без providerPaymentId,
        //повтор с тем же ключом пойдёт к провайдеру с тем же paymentId, а зависший добьёт джоба
        ProviderPayment providerPayment = paymentProvider.create(
                payment.getId(), payment.getAmount(), payment.getCurrency(), idempotencyKey
        );

        //3. Сохраняем ссылку и айдишник провайдера. Статус не проверяем: даже если джоба успела отменить платёж,
        //providerPaymentId нужен, чтобы webhook нашёл платёж, если юзер всё же оплатит
        return transactionTemplate.execute(status -> {
            Payment locked = paymentRepository.findByIdForUpdate(payment.getId())
                    .orElseThrow(() -> new PaymentNotFoundException("payment.not-found", payment.getId()));
            locked.setProviderPaymentId(providerPayment.providerPaymentId());
            locked.setConfirmationUrl(providerPayment.confirmationUrl());
            return toDto(locked);
        });
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

        Page<Payment> payments = paymentRepository.findByAccountId(accountId, pageable);

        return payments.map(this::toDto);
    }

    @Transactional
    public PaymentDto confirmPayment(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("payment.not-found", paymentId));
        return succeed(payment, false);//Админ не знает, были ли деньги на самом деле, поэтому отменённый не подтверждаем
    }

    //Провайдер сообщил об изменении платежа. Статусу из запроса не верим, а перепроверяем у провайдера
    public PaymentDto handleProviderNotification(String providerPaymentId) {
        ProviderStatus providerStatus = paymentProvider.fetchStatus(providerPaymentId);

        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByProviderPaymentIdForUpdate(paymentProvider.name(), providerPaymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(
                            "payment.not-found-by-provider", paymentProvider.name(), providerPaymentId));
            return switch (providerStatus) {
                case SUCCEEDED ->
                        succeed(payment, true);//Деньги у провайдера - зачисляем, даже если у нас платёж уже отменён
                case CANCELED -> cancel(payment);//Если отменен, то отменяем платеж и у нас
                case PENDING -> toDto(payment);//Если до сих пор на рассмотрении, то просто возращаем платеж
            };
        });
    }

    @Transactional
    public PaymentDto cancelPayment(UUID userId, UUID paymentId, boolean isAdmin) {
        Payment payment = findPayment(userId, paymentId, isAdmin);
        //Повторная отмена, уже оплаченный (409) и не дошедший до провайдера платёж - к провайдеру идти незачем
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getProviderPaymentId() == null) {
            return cancel(payment);
        }
        //Сначала отменяем у провайдера: юзер мог уже оплатить по выданной ссылке, тогда зачисляем деньги вместо отмены.
        //CANCELED у нас - только если провайдер подтвердил отмену, иначе по ссылке ещё можно заплатить
        return switch (paymentProvider.cancel(payment.getProviderPaymentId())) {//TODO(по хорошему вынести из транзакции)
            case CANCELED -> cancel(payment);
            case SUCCEEDED -> succeed(payment, true);//Юзер успел оплатить, зачисляем
            case PENDING -> throw new PaymentStatusConflictException("payment.cancel-not-possible", payment.getId());
        };
    }

    //Отмена неоплаченного платежа по таймауту из PendingPaymentsCancellingJob.
    //CANCELED у нас ставим, только если провайдер подтвердил отмену, иначе по старой ссылке ещё могут заплатить
    @Transactional
    public void expirePayment(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("payment.not-found", paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {//Пока ждали блокировку, платёж обработал webhook или юзер
            return;
        }
        if (payment.getProviderPaymentId() == null) {//У провайдера платёж так и не создали, отменять там нечего
            cancel(payment);
            return;
        }
        switch (paymentProvider.cancel(payment.getProviderPaymentId())) {
            case CANCELED -> cancel(payment);
            case SUCCEEDED -> succeed(payment, true);//Юзер успел заплатить, а webhook потерялся
            case PENDING -> log.warn("Провайдер не отменил платёж {}, попробуем в следующий запуск джобы", paymentId);
        }
    }

    //allowCanceled - зачислить и отменённый у нас платёж, если провайдер подтвердил, что деньги у него
    private PaymentDto succeed(Payment payment, boolean allowCanceled) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {//Повторное подтверждение — просто отдаём платёж
            return toDto(payment);
        }
        if (payment.getStatus() == PaymentStatus.CANCELED) {
            if (!allowCanceled) {//Отменённый платёж подтвердить нельзя
                throw new PaymentStatusConflictException("payment.status-conflict", payment.getId(), payment.getStatus());
            }
            log.warn("Платёж {} оплачен у провайдера после отмены, зачисляем деньги", payment.getId());
        }
        //Блокируем счёт, чтобы параллельные пополнения не потеряли деньги
        Account account = accountRepository.findByIdForUpdate(payment.getAccount().getId())
                .orElseThrow(() -> new AccountNotFoundException("account.not-found", payment.getAccount().getId()));

        if (account.getStatus() == AccountStatus.CLOSED) {
            if (!allowCanceled) {//Подтверждает админ, деньги у провайдера не подтверждены - возвращать нечего
                throw new AccountClosedException("account.closed", account.getId());
            }
            log.error("Платёж {} оплачен, но счёт {} закрыт, нужен возврат", payment.getId(), account.getId());
            //TODO(решить как возвращать бабки юзеру!
            // для dev делать не буду, когда подключим реального провайдера надо добавить метод refund)
            payment.setStatus(PaymentStatus.REFUND_REQUIRED);
            return toDto(payment);
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

    private PaymentDto cancel(Payment payment) {
        if (payment.getStatus() == PaymentStatus.CANCELED) {//Повторная отмена — просто отдаём платёж
            return toDto(payment);
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.REFUND_REQUIRED) {//Оплаченный платёж отменить нельзя
            throw new PaymentStatusConflictException("payment.status-conflict", payment.getId(), payment.getStatus());
        }

        payment.setStatus(PaymentStatus.CANCELED);

        return toDto(payment);
    }

    private Account findAccountByUserIdForUpdate(UUID userId) {
        return accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AccountNotFoundException("account.not-found-by-user", userId));
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
