package io.cvvexxx.billings.payment.job;

import io.cvvexxx.billings.payment.entity.PaymentStatus;
import io.cvvexxx.billings.payment.repository.PaymentRepository;
import io.cvvexxx.billings.payment.repository.PaymentRepository.StalePayment;
import io.cvvexxx.billings.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

//Отменяет платежи, которые юзер не оплатил за pending-ttl: брошенный PENDING иначе вечно мешает закрыть счёт.
//Заодно страхует от потерянных webhook'ов: если провайдер скажет, что платёж оплачен, деньги зачислятся
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentsCancellingJob {

    private static final int BATCH_SIZE = 100;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${app.payment.pending-ttl:PT20M}")
    private Duration pendingTtl;

    //fixedDelay: следующий запуск только после окончания предыдущего, запуски не наложатся.
    @Scheduled(fixedDelayString = "${app.payment.pending-payments-clean-up-interval:PT1M}")
    public void cancelPendingPayments() {
        Instant before = Instant.now().minus(pendingTtl);
        Instant after = Instant.EPOCH;
        List<StalePayment> batch;
        do {
            batch = paymentRepository.findStale(PaymentStatus.PENDING, after, before, Limit.of(BATCH_SIZE));
            for (StalePayment payment : batch) {
                try {
                    paymentService.expirePayment(payment.getId());
                } catch (Exception e) {//Один сбойный платёж не должен останавливать всю джобу
                    log.error("Не удалось обработать зависший платёж {}", payment.getId(), e);
                }
            }
            if (!batch.isEmpty()) {
                after = batch.getLast().getCreatedAt();
            }
        } while (batch.size() == BATCH_SIZE);
    }
}
