package io.cvvexxx.users.auth.controller;


import io.cvvexxx.users.auth.dto.ServiceTokenRequestDto;
import io.cvvexxx.users.auth.dto.ServiceTokenResponseDto;
import io.cvvexxx.users.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final AuthService authService;

    //Выпуск access-токена для межсервисных запросов (client credentials), refresh-токена нет - по истечении просят новый
    @PostMapping("/service-token") //POST /internal/auth/service-token
    public ResponseEntity<ServiceTokenResponseDto> getServiceToken(
            @Valid @RequestBody ServiceTokenRequestDto requestDto
    ) {
        return ResponseEntity.ok(authService.getServiceToken(requestDto));
    }
}
