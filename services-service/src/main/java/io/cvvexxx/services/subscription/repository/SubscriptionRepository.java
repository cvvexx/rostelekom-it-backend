package io.cvvexxx.services.subscription.repository;

import io.cvvexxx.services.subscription.entity.Subscription;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @EntityGraph(attributePaths = "tariff")
    Page<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "tariff")
    Optional<Subscription> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "tariff")
    Optional<Subscription> findWithTariffById(UUID id);

    boolean existsByUserIdAndTariffIdAndStatusIn(
            UUID userId, UUID tariffId, Collection<SubscriptionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s join fetch s.tariff where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s join fetch s.tariff where s.id = :id and s.userId = :userId")
    Optional<Subscription> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    //Забирает пачку подписок к списанию и ставит lease одним запросом. Строки блокируются только на время запроса:
    //skip locked - параллельные инстансы берут разные подписки, lease - пока идёт HTTP в billing, подписку никто не возьмёт
    @Transactional
    @Modifying
    @Query(value = """
            update subscriptions s
            set charge_locked_until = :lockedUntil
            from tariffs t
            where t.id = s.tariff_id
              and s.id in (
                select id from subscriptions
                where status in ('ACTIVE', 'SUSPENDED')
                  and next_charge_at <= :now
                  and (charge_locked_until is null or charge_locked_until <= :now)
                order by next_charge_at
                limit :batchSize
                for update skip locked
              )
            returning s.id as id, s.user_id as userId, s.price as price, s.status as status,
                      s.next_charge_at as nextChargeAt, s.cancel_at_period_end as cancelAtPeriodEnd,
                      t.name as tariffName
            """, nativeQuery = true)
    List<DueSubscription> claimDueForCharge(@Param("now") Instant now, @Param("lockedUntil") Instant lockedUntil,
                                            @Param("batchSize") int batchSize);

    interface DueSubscription {
        UUID getId();

        UUID getUserId();

        BigDecimal getPrice();

        SubscriptionStatus getStatus();

        Instant getNextChargeAt();

        boolean getCancelAtPeriodEnd();

        String getTariffName();
    }
}