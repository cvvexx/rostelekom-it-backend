package io.cvvexxx.users.auth.controller;

import io.cvvexxx.users.auth.dto.*;
import io.cvvexxx.users.auth.service.AuthService;
import io.cvvexxx.users.otp.dto.OtpChallengeDto;
import io.cvvexxx.users.token.dto.TokenPairDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //Аннотация для спринга чтобы он создал singleton-бин этого класса, также означает, что этот класс принимает http-запросы (аналог APIRouter в FastAPI)
@RequiredArgsConstructor //Lombok-аннотация, которая неявно создает конструктор, включающий в себя все final-поля, через него Spring внедряет зависимости (как Depends() в FastAPI)
@Slf4j //Lombok-аннотация, которая неявно создает логгер (log) в этом классе (как log = logging.getLogger(__name__))
@RequestMapping("/api/auth") //Префикс всех эндоинтов этого класса (как APIRouter(prefix="/api/auth"))
@SecurityRequirements //Пустая аннотация: в Swagger эндпоинты помечаются как публичные, без замка
public class AuthController {

    // Внедряем в класс Service, чтобы делигировать ему бизнес-логику
    private final AuthService authService;

    //Реквест на получение кода
    //@RequestBody - берем dto из JSON тела запроса (в FastAPI pydantic-модель в параметрах берется из тела сама, тут надо явно)
    //@Valid - запускает проверку аннотаций внутри DTO (@NotBlank, @Size...), без нее они просто игнорируются.
    //Если проверка не прошла - метод даже не вызовется, ошибку обработает GlobalExceptionHandler.handleBindException
    @PostMapping("/otp/request") //POST /api/auth/otp/request, аналог @router.post("/otp/request")
    public ResponseEntity<OtpChallengeDto> requestOtp(@RequestBody @Valid OtpRequestDto dto) {
        return ResponseEntity.ok(authService.requestOtp(dto));//Возвращаем 200, а в теле то, что возвращает service(OtpChallengeDto)
    }

    //Реквест на проверку кода по номеру телефона
    @PostMapping("/otp/verify") //POST /api/auth/otp/verify
    public ResponseEntity<TokenPairDto> verifyOtp(@RequestBody @Valid OtpVerifyDto dto) {
        return ResponseEntity.ok(authService.verifyOtp(dto));//Возвращаем 200, токены в JSON
    }

    @PostMapping("/refresh") //POST /api/auth/refresh
    public ResponseEntity<TokenPairDto> refresh(@RequestBody @Valid RefreshTokenDto dto) {
        return ResponseEntity.ok(authService.refresh(dto.refreshToken()));
    }

    @PostMapping("/logout") //POST /api/auth/logout, 204 и пустое тело
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenDto dto) {
        authService.logout(dto.refreshToken());
        return ResponseEntity.noContent().build();
    }

    //Выпуск access-токена для межсервисных запросов (client credentials), refresh-токена нет - по истечении просят новый
    @PostMapping("/service-token") //POST /api/auth/service-token
    public ResponseEntity<ServiceTokenResponseDto> getServiceToken(
            @Valid @RequestBody ServiceTokenRequestDto requestDto
    ) {
        return ResponseEntity.ok(authService.getServiceToken(requestDto));
    }
}
