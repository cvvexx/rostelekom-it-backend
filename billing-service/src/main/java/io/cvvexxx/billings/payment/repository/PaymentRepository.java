package io.cvvexxx.billings.payment.repository;

import io.cvvexxx.billings.payment.entity.Payment;
import io.cvvexxx.billings.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);

    Optional<Payment> findByIdAndAccountId(UUID id,  UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findByProviderPaymentIdForUpdate(String provider, String providerPaymentId);

    //Для того чтобы обрабатывать зависшие платежи
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant before);

}
