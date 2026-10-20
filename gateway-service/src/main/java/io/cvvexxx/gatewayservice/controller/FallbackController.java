package io.cvvexxx.gatewayservice.controller;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController //Класс с эндпоинтами, все что возвращают методы отдается как JSON. Аналог APIRouter в FastAPI
public class FallbackController {

    @RequestMapping("/fallback/users") //Без указания метода ловит любой HTTP-метод, как @router.api_route("/fallback/users")
    public ResponseEntity<Map<String, String>> usersServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        Map.of(
                                "error", "users_service_unavailable",
                                "message", "Сервис пользователей временно недоступен, попробуйте позже"
                        )
                );
    }

    @RequestMapping("/fallback/billing") //Без указания метода ловит любой HTTP-метод, как @router.api_route("/fallback/users")
    public ResponseEntity<Map<String, String>> billingsServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        Map.of(
                                "error", "billings_service_unavailable",
                                "message", "Сервис платежей временно недоступен, попробуйте позже"
                        )
                );
    }

}
