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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing/payments")
public class PaymentController {

    private final PaymentService paymentService;


    //TODO(добавить смс-подтверждение(заглушку хотя бы))
    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(//TODO(добавить информацию о том, создан ли аккаунт)
            @AuthenticationPrincipal Jwt jwt,
            @Size(max = 64, message = "{validation.payment-create.idempotency-key.too-long}") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreatePaymentDto createPaymentDto
    ) {
        UUID userId = currentUserId(jwt);
        return ResponseEntity.ok(paymentService.createPayment(userId, createPaymentDto.amount(), idempotencyKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> getMyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(paymentService.getMyPayment(currentUserId(jwt), id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PaymentDto> confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return ResponseEntity.ok(paymentService.confirmPayment(currentUserId(jwt), id, isAdmin));
    }

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
