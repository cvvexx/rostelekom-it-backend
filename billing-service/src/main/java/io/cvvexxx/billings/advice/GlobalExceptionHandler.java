package io.cvvexxx.billings.advice;

import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.exception.AccountStatusConflictException;
import io.cvvexxx.billings.payment.exception.IdempotencyKeyReusedException;
import io.cvvexxx.billings.payment.exception.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

//Переводит исключения в ProblemDetail, так же как GlobalExceptionHandler в user-service
@RestControllerAdvice
public class GlobalExceptionHandler {

    //Нет счёта или платежа (в том числе чужого платежа, чтобы не светить что он существует)
    @ExceptionHandler({AccountNotFoundException.class, PaymentNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    //Операция со счётом недопустима в его текущем статусе (закрыт, статус уже стоит)
    @ExceptionHandler({AccountClosedException.class, AccountStatusConflictException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    //Тот же Idempotency-Key пришёл с другим телом запроса
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    //HTTP 401(токен не валиден, истек или его вообще нет), вызывается из ProblemDetailAuthenticationEntryPoint
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

    //Ошибки @Valid в теле запроса (@NotNull, @Positive и т.д. в DTO)
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException e) {
        Map<String, String> errors = e.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (first, second) -> first + "; " + second));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    //Ошибки валидации параметров метода контроллера, например @Size на заголовке Idempotency-Key.
    //Если у метода есть такие параметры, Spring кладёт сюда и ошибки @Valid из тела вместо BindException
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        //Ошибки полей DTO из тела запроса
        e.getBeanResults().forEach(result -> result.getFieldErrors().forEach(fe ->
                errors.merge(fe.getField(), String.valueOf(fe.getDefaultMessage()), (first, second) -> first + "; " + second)));
        //Ошибки простых параметров (заголовки, query-параметры)
        e.getValueResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.merge(String.valueOf(result.getMethodParameter().getParameterName()),
                        String.valueOf(error.getDefaultMessage()), (first, second) -> first + "; " + second)));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
