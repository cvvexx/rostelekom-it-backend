package io.cvvexxx.billings.payment.repository;

import io.cvvexxx.billings.payment.entity.Payment;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);

    Optional<Payment> findByIdAndAccountId(UUID id, UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.account.userId = :userId")
    Optional<Payment> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    //Для того чтобы обрабатывать зависшие платежи
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant before);

    //Как в AccountRepository: два запроса с одним idempotencyKey не упадут на unique,
    //второй просто ничего не вставит, после чего платёж перечитывается через findByAccountIdAndIdempotencyKey
    @Modifying
    @Query(value = """
            insert into payments (id, account_id, amount, currency, provider, idempotency_key)
            values (gen_random_uuid(), :accountId, :amount, :currency, :provider, :idempotencyKey)
            on conflict (account_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID accountId, BigDecimal amount, String currency, String provider, String idempotencyKey);

    Page<Payment> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
