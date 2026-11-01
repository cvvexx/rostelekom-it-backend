package io.cvvexxx.billings.charges.controller;


import io.cvvexxx.billings.charges.dto.ChargeDto;
import io.cvvexxx.billings.charges.dto.ChargeResult;
import io.cvvexxx.billings.charges.dto.CreateChargeDto;
import io.cvvexxx.billings.charges.service.ChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/billing/charges")
public class ChargeController {

    private final ChargeService chargeService;

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<ChargeDto> createCharge(@RequestBody @Valid CreateChargeDto createChargeDto) {
        ChargeResult result = chargeService.charge(createChargeDto);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.charge());
    }

}
