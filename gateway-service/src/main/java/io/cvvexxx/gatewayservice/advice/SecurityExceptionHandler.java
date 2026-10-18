package io.cvvexxx.gatewayservice.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice //Глобальный обработчик ошибок для всех контроллеров, в FastAPI это как набор @app.exception_handler(...)
public class SecurityExceptionHandler {

    @ExceptionHandler(AuthenticationException.class) //Аналог @app.exception_handler(AuthenticationException), HTTP 401
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Access token is missing, expired or invalid");
    }

    @ExceptionHandler(AccessDeniedException.class) //Аналогично, только для AccessDeniedException, HTTP 403
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Not enough permissions to access this resource");
    }
}
