package io.cvvexxx.users.auth.controller;

import io.cvvexxx.users.auth.dto.OtpRequestDto;
import io.cvvexxx.users.auth.dto.OtpVerifyDto;
import io.cvvexxx.users.auth.dto.RefreshTokenDto;
import io.cvvexxx.users.auth.service.AuthService;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.token.dto.TokenPairDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //Аннотация для спринга чтобы он создал singleton-бин этого класса, также означает, что этот класс принимает http-запросы
@RequiredArgsConstructor //Lombok-аннотация, которая неявно создает конструктор, включающий в себя все final-поля
@Slf4j //Lombok-аннотация, которая неявно создает логгер (log) в этом классе
@RequestMapping("/api/auth") //Префикс всех эндоинтов этого класса
public class AuthController {

    // Внедряем в класс Service, чтобы делигировать ему бизнес-логику
    private final AuthService authService;

    //Реквест на получение кода
    @PostMapping("/otp/request")
    public ResponseEntity<OtpChallengeDto> requestOtp(@RequestBody @Valid OtpRequestDto dto) {
        return ResponseEntity.ok(authService.requestOtp(dto));//Возвращаем 200, а в теле то, что возвращает service(OtpChallengeDto)
    }

    //Реквест на проверку кода по номеру телефона
    @PostMapping("/otp/verify")
    public ResponseEntity<TokenPairDto> verifyOtp(@RequestBody @Valid OtpVerifyDto dto) {
        return ResponseEntity.ok(authService.verifyOtp(dto));//Возвращаем 200, токены в JSON
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairDto> refresh(@RequestBody @Valid RefreshTokenDto dto) {
        return ResponseEntity.ok(authService.refresh(dto.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenDto dto) {
        authService.logout(dto.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
