package io.cvvexxx.gatewayservice.controller;


import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //Класс с эндпоинтами, все что возвращают методы отдается как JSON. Аналог APIRouter в FastAPI
public class FallbackController {

    //Достаём текст ошибки из messages.properties на языке клиента (заголовок Accept-Language)
    private final MessageSource messageSource;

    public FallbackController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    //Тот же ProblemDetail, что и у остальных ошибок, чтобы фронт разбирал один формат
    private ProblemDetail unavailable(String code) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                messageSource.getMessage(code, null, LocaleContextHolder.getLocale()));
    }

    @RequestMapping("/fallback/users") //Без указания метода ловит любой HTTP-метод, как @router.api_route("/fallback/users")
    public ProblemDetail usersServiceFallback() {
        return unavailable("error.users-service-unavailable");
    }

    @RequestMapping("/fallback/billing") //Без указания метода ловит любой HTTP-метод, как @router.api_route("/fallback/billing")
    public ProblemDetail billingsServiceFallback() {
        return unavailable("error.billing-service-unavailable");
    }

}
