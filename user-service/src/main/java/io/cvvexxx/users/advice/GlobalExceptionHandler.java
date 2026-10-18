package io.cvvexxx.users.advice;

import io.cvvexxx.users.otp.exception.InvalidOtpException;
import io.cvvexxx.users.otp.exception.OtpRequestNotAllowedException;
import io.cvvexxx.users.phone.InvalidPhoneNumberException;
import io.cvvexxx.users.token.exception.InvalidRefreshTokenException;
import io.cvvexxx.users.user.exception.FieldAlreadyExistsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

//Обработчик ошибок, который перехватывает ошибки, выброшенные в рантайме
// и возвращает понятный JSON объект с описанием проблемы
@RestControllerAdvice
public class GlobalExceptionHandler {

    //Ошибка создания юзера, нарушение уникальности username, номера и т.п. уникальных полей
    @ExceptionHandler(FieldAlreadyExistsException.class)
    public ProblemDetail handleFieldAlreadyExists(FieldAlreadyExistsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    //Не удалось распарсить телефон(пришли буквы, телефон не соответствует стране)
    //По идее должно обрабатываться на фронте, но на всякий случай сделал
    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ProblemDetail handleInvalidPhoneNumber(InvalidPhoneNumberException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    //Ошибка выбрасывается в DefaultOtpService.java 81-87 строки
    //Причины ошибки описаны в RedisOtpStore.java 71-74 строки
    @ExceptionHandler(InvalidOtpException.class)
    public ProblemDetail handleInvalidOtp(InvalidOtpException e) {
        //Ставим HTTP статус
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        //Ставим поле в JSON
        problem.setProperty("reason", e.getReason().name());
        problem.setProperty("attemptsLeft", e.getAttemptsLeft());
        return problem;
    }

    //Ошибка выбрасывается, когда у юзера не прошел кд с предыдущего запроса/Слишком много запросов за окно
    @ExceptionHandler(OtpRequestNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleOtpRequestNotAllowed(OtpRequestNotAllowedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setProperty("retryAfterSeconds", e.getRetryAfter().toSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfter().toSeconds()))
                .body(problem);
    }

    //Неправильный refresh-токен
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    //Выбрасывается, когда не нашлось какой-то сущности в базе(юзера, роли)
    //TODO(Написать кастомный exception для каждой сущности)
    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    //HTTP 401(токен не валиден, истек или его вообще нет)
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Access token is missing, expired or invalid");
    }

    //HTTP 403(не хватает прав доступа)
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Not enough permissions to access this resource");
    }

    //Ошибка валидации входных данных
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException e) {
        Map<String, String> errors = e.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (first, second) -> first + "; " + second));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        //В JSON добавляется проперти errors - СПИСОК ошибок валидации
        problem.setProperty("errors", errors);
        return problem;
    }
}
