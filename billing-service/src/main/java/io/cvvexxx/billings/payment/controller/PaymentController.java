package io.cvvexxx.billings.payment.controller;


import io.cvvexxx.billings.payment.dto.CreatePaymentDto;
import io.cvvexxx.billings.payment.dto.PaymentDto;
import io.cvvexxx.billings.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

//Контроллер платежей (пополнение счёта)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing/payments")
public class PaymentController {

    private final PaymentService paymentService;


    //TODO(добавить смс-подтверждение(заглушку хотя бы))
    //Создание платежа. Idempotency-Key защищает от дублей, если фронт отправит запрос повторно
    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(//TODO(добавить информацию о том, создан ли аккаунт)
            @AuthenticationPrincipal Jwt jwt,
            @Size(max = 64, message = "{validation.payment-create.idempotency-key.too-long}") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreatePaymentDto createPaymentDto
    ) {
        UUID userId = currentUserId(jwt);
        return ResponseEntity.ok(paymentService.createPayment(userId, createPaymentDto.amount(), idempotencyKey));
    }

    //Только свой платёж, на чужой будет 404
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> getMyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(paymentService.getMyPayment(currentUserId(jwt), id));
    }

    //Ручное подтверждение платежа админом
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentDto> confirmPayment(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(paymentService.confirmPayment(id));
    }

    //Свой платёж может отменить юзер, любой - админ
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentDto> cancelPayment(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return ResponseEntity.ok(paymentService.cancelPayment(currentUserId(jwt), id, isAdmin));
    }

    //Все платежи юзера, новые сверху
    @GetMapping
    public ResponseEntity<PagedModel<PaymentDto>> getMyPayments(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(new PagedModel<>(paymentService.getMyPayments(currentUserId(jwt), pageable)));
    }


    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

}
