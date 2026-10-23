package io.cvvexxx.gatewayservice.advice;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice //Глобальный обработчик ошибок для всех контроллеров, в FastAPI это как набор @app.exception_handler(...)
public class SecurityExceptionHandler {

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
}
