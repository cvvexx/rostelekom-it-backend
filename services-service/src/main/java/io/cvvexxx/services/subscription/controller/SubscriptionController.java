package io.cvvexxx.services.subscription.controller;

import io.cvvexxx.services.advice.InvalidSortException;
import io.cvvexxx.services.subscription.dto.CreateSubscriptionDto;
import io.cvvexxx.services.subscription.dto.SubscriptionDto;
import io.cvvexxx.services.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/services/subscriptions")
public class SubscriptionController {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "nextChargeAt", "price", "status");

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionDto> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CreateSubscriptionDto createSubscriptionDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.subscribe(currentUserId(jwt), createSubscriptionDto.tariffId()));
    }

    @GetMapping("/my")
    public ResponseEntity<PagedModel<SubscriptionDto>> getMySubscriptions(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        InvalidSortException.requireAllowed(pageable, SORT_FIELDS);
        return ResponseEntity.ok(new PagedModel<>(subscriptionService.getMySubscriptions(currentUserId(jwt), pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionDto> getSubscription(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(subscriptionService.getSubscription(currentUserId(jwt), id, isAdmin(authentication)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionDto> cancelSubscription(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam(name = "immediate", defaultValue = "false") boolean immediate
    ) {
        return ResponseEntity.ok(
                subscriptionService.cancelSubscription(currentUserId(jwt), id, immediate, isAdmin(authentication))
        );
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}