package io.cvvexxx.services.advice;

import io.cvvexxx.services.subscription.exception.SubscriptionAlreadyCanceledException;
import io.cvvexxx.services.subscription.exception.SubscriptionAlreadyExistsException;
import io.cvvexxx.services.subscription.exception.SubscriptionNotFoundException;
import io.cvvexxx.services.subscription.exception.TariffInactiveException;
import io.cvvexxx.services.tariff.exception.TariffAlreadyExistsException;
import io.cvvexxx.services.tariff.exception.TariffNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler({TariffNotFoundException.class, SubscriptionNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(LocalizedException e, Locale locale) {
        return problem(HttpStatus.NOT_FOUND, e, locale);
    }

    @ExceptionHandler({
            TariffAlreadyExistsException.class,
            TariffInactiveException.class,
            SubscriptionAlreadyExistsException.class,
            SubscriptionAlreadyCanceledException.class
    })
    public ResponseEntity<ProblemDetail> handleConflict(LocalizedException e, Locale locale) {
        return problem(HttpStatus.CONFLICT, e, locale);
    }

    @ExceptionHandler(InvalidSortException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(LocalizedException e, Locale locale) {
        return problem(HttpStatus.BAD_REQUEST, e, locale);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e, Locale locale) {
        String detail = messageSource.getMessage("error.validation-failed", null, locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", "error.validation-failed");

        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first
                ));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e, Locale locale) {
        String detail = messageSource.getMessage("error.forbidden", null, locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problem.setProperty("code", "error.forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandled(Exception e, Locale locale) {
        log.error("Unhandled exception", e);
        String detail = messageSource.getMessage("error.internal", null, locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
        problem.setProperty("code", "error.internal");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, LocalizedException e, Locale locale) {
        String detail = messageSource.getMessage(e.getMessage(), e.getArgs(), e.getMessage(), locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", e.getMessage());
        return ResponseEntity.status(status).body(problem);
    }
}