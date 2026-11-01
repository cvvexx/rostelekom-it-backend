package io.cvvexxx.gatewayservice.advice;

import io.cvvexxx.gatewayservice.config.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

//ResponseEntityExceptionHandler переводит в ProblemDetail стандартные ошибки Spring MVC (404, неверный метод и т.п.)
@RestControllerAdvice //Глобальный обработчик ошибок для всех контроллеров, в FastAPI это как набор @app.exception_handler(...)
public class SecurityExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    //Достаём текст ошибки из messages.properties на языке клиента (заголовок Accept-Language)
    private final MessageSource messageSource;

    public SecurityExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    @ExceptionHandler(AuthenticationException.class) //Аналог @app.exception_handler(AuthenticationException), HTTP 401
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                message("error.unauthorized"));
    }

    @ExceptionHandler(AccessDeniedException.class) //Аналогично, только для AccessDeniedException, HTTP 403
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                message("error.forbidden"));
    }

    @ExceptionHandler(RateLimitExceededException.class) //Слишком много запросов, HTTP 429
    public ProblemDetail handleRateLimit(RateLimitExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                message("error.too-many-requests"));
    }

    //Всё, что не поймали выше: клиенту 500 без подробностей, стектрейс только в лог
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message("error.internal"));
    }
}
