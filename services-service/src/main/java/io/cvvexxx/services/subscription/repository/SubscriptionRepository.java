package io.cvvexxx.services.subscription.repository;

import io.cvvexxx.services.subscription.entity.Subscription;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query(value = """
            select id from subscriptions
            where status in ('ACTIVE', 'SUSPENDED')
              and next_charge_at <= :now
            order by next_charge_at asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<UUID> findDueForChargeIds(@Param("now") Instant now, @Param("batchSize") int batchSize);
}