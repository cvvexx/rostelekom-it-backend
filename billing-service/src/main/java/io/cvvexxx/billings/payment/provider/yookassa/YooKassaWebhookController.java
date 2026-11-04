package io.cvvexxx.billings.payment.provider.yookassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.cvvexxx.billings.payment.exception.PaymentNotFoundException;
import io.cvvexxx.billings.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(YooKassaWebhookController.PATH)
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "yookassa")
public class YooKassaWebhookController {

    public static final String PATH = "/api/billing/yookassa/webhook";

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody Notification notification) {
        try {
            paymentService.handleProviderNotification(notification.object().id());
        } catch (PaymentNotFoundException e) {
            log.warn("Уведомление ЮKassa {} о неизвестном платеже {}", notification.event(), notification.object().id());
        }
        return ResponseEntity.ok().build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Notification(String event, NotificationObject object) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NotificationObject(String id) {
    }
}
