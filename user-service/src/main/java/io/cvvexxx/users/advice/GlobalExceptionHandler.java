package io.cvvexxx.users.advice;

import io.cvvexxx.users.auth.exception.FullNameRequiredException;
import io.cvvexxx.users.otp.exception.InvalidOtpException;
import io.cvvexxx.users.otp.exception.OtpRequestNotAllowedException;
import io.cvvexxx.users.phone.InvalidPhoneNumberException;
import io.cvvexxx.users.token.exception.InvalidRefreshTokenException;
import io.cvvexxx.users.user.exception.UserNotFoundException;
import org.springframework.http.HttpHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.stream.Collectors;

//Обработчик ошибок, который перехватывает ошибки, выброшенные в рантайме
// и возвращает понятный JSON объект с описанием проблемы
//ResponseEntityExceptionHandler переводит в ProblemDetail стандартные ошибки Spring MVC
//(битый JSON, нет заголовка, неверный метод, 404 и т.п.), иначе они уходят клиенту в другом формате
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice //В FastAPI это как набор @app.exception_handler(...), работает сразу для всех контроллеров
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    //Достаём текст ошибки из messages.properties на языке клиента (заголовок Accept-Language)
    private final MessageSource messageSource;

    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private String message(LocalizedException e) {
        return message(e.getMessage(), e.getArgs());
    }

    //@ExceptionHandler(X.class) - аналог @app.exception_handler(X): если X вылетел в любом контроллере/сервисе,
    //Spring вызовет этот метод, а то что он вернет уйдет клиенту

    //Не удалось распарсить телефон(пришли буквы, телефон не соответствует стране)
    //По идее должно обрабатываться на фронте, но на всякий случай сделал
    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ProblemDetail handleInvalidPhoneNumber(InvalidPhoneNumberException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(e));
    }

    //Новый номер входит без фамилии/имени (DefaultAuthService.verifyOtp)
    @ExceptionHandler(FullNameRequiredException.class)
    public ProblemDetail handleFullNameRequired(FullNameRequiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(e));
    }

    //Ошибка выбрасывается в DefaultOtpService.java 81-87 строки
    //Причины ошибки описаны в RedisOtpStore.java 71-74 строки
    @ExceptionHandler(InvalidOtpException.class)
    public ProblemDetail handleInvalidOtp(InvalidOtpException e) {
        //Ставим HTTP статус
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(e));
        //Ставим поле в JSON
        problem.setProperty("reason", e.getReason().name());
        problem.setProperty("attemptsLeft", e.getAttemptsLeft());
        return problem;
    }

    //Ошибка выбрасывается, когда у юзера не прошел кд с предыдущего запроса/Слишком много запросов за окно
    @ExceptionHandler(OtpRequestNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleOtpRequestNotAllowed(OtpRequestNotAllowedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, message(e));
        problem.setProperty("retryAfterSeconds", e.getRetryAfter().toSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfter().toSeconds()))
                .body(problem);
    }

    //Неправильный refresh-токен
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message(e));
    }

    //Выбрасывается, когда не нашлось какой-то сущности в базе(юзера)
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message(e));
    }

    //HTTP 401(токен не валиден, истек или его вообще нет)
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

    //Ошибка валидации входных данных
    //Сюда попадают ошибки @Valid (@NotBlank, @Size и т.д. в DTO), аналог RequestValidationError в FastAPI, только отдаем 400, а не 422
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = e.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (first, second) -> first + "; " + second));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message("error.validation-failed"));
        //В JSON добавляется проперти errors - СПИСОК ошибок валидации
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
