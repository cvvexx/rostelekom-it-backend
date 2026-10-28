package io.cvvexxx.billings.advice;

import io.cvvexxx.billings.account.exception.AccountClosedException;
import io.cvvexxx.billings.account.exception.AccountHasPendingPaymentsException;
import io.cvvexxx.billings.account.exception.AccountNotFoundException;
import io.cvvexxx.billings.account.exception.AccountStatusConflictException;
import io.cvvexxx.billings.payment.exception.IdempotencyKeyReusedException;
import io.cvvexxx.billings.payment.exception.PaymentNotFoundException;
import io.cvvexxx.billings.payment.exception.PaymentStatusConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

//Переводит исключения в ProblemDetail, так же как GlobalExceptionHandler в user-service.
//Стандартные ошибки Spring MVC (битый JSON, нет заголовка, 404 и т.п.) переводит ResponseEntityExceptionHandler
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    //Достаём текст ошибки из messages.properties на языке клиента (заголовок Accept-Language)
    private final MessageSource messageSource;

    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private String message(LocalizedException e) {
        return message(e.getMessage(), e.getArgs());
    }

    //Нет счёта или платежа (в том числе чужого платежа, чтобы не светить что он существует)
    @ExceptionHandler({AccountNotFoundException.class, PaymentNotFoundException.class})
    public ProblemDetail handleNotFound(LocalizedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message(e));
    }

    //Операция со счётом недопустима в его текущем статусе (закрыт, статус уже стоит, есть неоплаченные платежи)
    @ExceptionHandler({AccountClosedException.class, AccountStatusConflictException.class, PaymentStatusConflictException.class,
            AccountHasPendingPaymentsException.class})
    public ProblemDetail handleConflict(LocalizedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message(e));
    }

    //Тот же Idempotency-Key пришёл с другим телом запроса
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, message(e));
    }

    //Клиент прислал sort по полю, которого нет в списке разрешённых (sort=foo, sort=["string"] из Swagger)
    @ExceptionHandler(InvalidSortException.class)
    public ProblemDetail handleInvalidSort(InvalidSortException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(e));
    }

    //HTTP 401(токен не валиден, истек или его вообще нет), вызывается из ProblemDetailAuthenticationEntryPoint
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                message("error.unauthorized"));
    }

    //HTTP 403(не хватает прав доступа)
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                message("error.forbidden"));
    }

    //Ошибки @Valid в теле запроса (@NotNull, @Positive и т.д. в DTO)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = e.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (first, second) -> first + "; " + second));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message("error.validation-failed"));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    //Ошибки валидации параметров метода контроллера, например @Size на заголовке Idempotency-Key.
    //Если у метода есть такие параметры, Spring кладёт сюда и ошибки @Valid из тела вместо BindException
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        //Ошибки полей DTO из тела запроса
        e.getBeanResults().forEach(result -> result.getFieldErrors().forEach(fe ->
                errors.merge(fe.getField(), String.valueOf(fe.getDefaultMessage()), (first, second) -> first + "; " + second)));
        //Ошибки простых параметров (заголовки, query-параметры)
        e.getValueResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.merge(String.valueOf(result.getMethodParameter().getParameterName()),
                        String.valueOf(error.getDefaultMessage()), (first, second) -> first + "; " + second)));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message("error.validation-failed"));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    //Всё, что не поймали выше: клиенту 500 без подробностей, стектрейс только в лог
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message("error.internal"));
    }
}
