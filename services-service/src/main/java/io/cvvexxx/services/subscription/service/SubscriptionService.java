package io.cvvexxx.services.subscription.service;

import io.cvvexxx.services.client.BillingServiceRestClient;
import io.cvvexxx.services.client.ChargeOutcome;
import io.cvvexxx.services.subscription.dto.CreateChargeDto;
import io.cvvexxx.services.subscription.dto.SubscriptionDto;
import io.cvvexxx.services.subscription.entity.Subscription;
import io.cvvexxx.services.subscription.entity.SubscriptionStatus;
import io.cvvexxx.services.subscription.exception.*;
import io.cvvexxx.services.subscription.repository.SubscriptionRepository;
import io.cvvexxx.services.tariff.entity.Tariff;
import io.cvvexxx.services.tariff.exception.TariffNotFoundException;
import io.cvvexxx.services.tariff.repository.TariffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    @Value("${app.subscription.lease-ttl:300000}")
    private long leaseTtl;

    private final SubscriptionRepository subscriptionRepository;
    private final TariffRepository tariffRepository;
    private final TransactionTemplate transactionTemplate;
    private final BillingServiceRestClient billingClient;

    public SubscriptionDto subscribe(UUID userId, UUID tariffId) {
        UUID subscriptionId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant nextChargeAt = now.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
        Instant lockedUntil = now.plus(Duration.ofMillis(leaseTtl));

        //резервация активной подписки в БД
        Tariff tariff = transactionTemplate.execute(status -> {
            Tariff found = tariffRepository.findById(tariffId)
                    .orElseThrow(() -> new TariffNotFoundException("tariff.not-found", tariffId));
            if (!found.isActive()) {
                throw new TariffInactiveException("tariff.inactive", tariffId);
            }

            int inserted = subscriptionRepository.insertActiveIfAbsent(
                    subscriptionId,
                    userId,
                    tariffId,
                    found.getPrice(),
                    found.getCurrency(),
                    now,
                    nextChargeAt,
                    lockedUntil
            );
            if (inserted == 0) {
                throw new SubscriptionAlreadyExistsException("subscription.already-exists", tariffId);
            }
            return found;
        });

        //списание
        ChargeOutcome outcome;
        try {
            outcome = billingClient.createCharge(new CreateChargeDto(
                    userId,
                    tariff.getPrice(),
                    subscriptionId + ":" + now.getEpochSecond(),
                    "Оплата тарифа " + tariff.getName()
            ));
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> subscriptionRepository.deleteById(subscriptionId));
            throw e;
        }

        //сохраняем транзакцию или удаляем черновик при отказе
        return switch (outcome) {
            case ChargeOutcome.Charged charged -> transactionTemplate.execute(status -> {
                Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                        .orElseThrow(() -> new SubscriptionNotFoundException("subscription.not-found", subscriptionId));
                subscription.setLastChargeTransactionId(charged.transactionId());
                subscription.setChargeLockedUntil(null);
                return toDto(subscription);
            });
            case ChargeOutcome.Declined declined -> {
                transactionTemplate.executeWithoutResult(status -> subscriptionRepository.deleteById(subscriptionId));
                throw new ChargeDeclinedException(declined.code(), userId);
            }
            case ChargeOutcome.AccountClosed ignored -> {
                transactionTemplate.executeWithoutResult(status -> subscriptionRepository.deleteById(subscriptionId));
                throw new ChargeDeclinedException("charge.account-closed", userId);
            }
        };
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionDto> getMySubscriptions(UUID userId, Pageable pageable) {
        return subscriptionRepository.findByUserId(userId, pageable).map(this::toDto);
    }


    @Transactional(readOnly = true)
    public SubscriptionDto getSubscription(UUID userId, UUID subscriptionId, boolean isAdmin) {
        Subscription subscription = (isAdmin
                ? subscriptionRepository.findWithTariffById(subscriptionId)
                : subscriptionRepository.findByIdAndUserId(subscriptionId, userId))
                .orElseThrow(() -> new SubscriptionNotFoundException("subscription.not-found", subscriptionId));
        return toDto(subscription);
    }

    @Transactional
    public SubscriptionDto cancelSubscription(UUID userId, UUID subscriptionId, boolean immediate, boolean isAdmin) {
        Subscription subscription = (isAdmin
                ? subscriptionRepository.findByIdForUpdate(subscriptionId)
                : subscriptionRepository.findByIdAndUserIdForUpdate(subscriptionId, userId))
                .orElseThrow(() -> new SubscriptionNotFoundException("subscription.not-found", subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new SubscriptionAlreadyCanceledException("subscription.already-canceled", subscriptionId);
        }

        if (immediate || subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setCanceledAt(Instant.now());
            subscription.setCancelAtPeriodEnd(false);
            subscription.setChargeLockedUntil(null);
        } else {
            subscription.setCancelAtPeriodEnd(true);
        }

        return toDto(subscription);
    }


    private SubscriptionDto toDto(Subscription s) {
        Tariff tariff = s.getTariff();
        return new SubscriptionDto(
                s.getId(),
                s.getUserId(),
                tariff.getId(),
                tariff.getName(),
                tariff.getCategory(),
                s.getStatus(),
                s.getPrice(),
                s.getCurrency(),
                tariff.getBillingPeriod(),
                s.getCurrentPeriodStart(),
                s.getNextChargeAt(),
                s.isCancelAtPeriodEnd(),
                s.getCanceledAt(),
                s.getLastChargeTransactionId(),
                s.getCreatedAt()
        );
    }
}
