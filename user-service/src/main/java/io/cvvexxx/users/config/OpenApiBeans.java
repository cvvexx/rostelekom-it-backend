package io.cvvexxx.users.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//Настройка Swagger: UI доступен по /swagger-ui.html, спека по /v3/api-docs
@Configuration
public class OpenApiBeans {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .description("Вход по номеру телефона (OTP), JWT-токены, профиль пользователя")
                        .version("v1"))
                //Кнопка Authorize в UI: вставляем access-токен из /api/auth/otp/verify, и он уходит в заголовке
                //Authorization: Bearer <token> во все запросы
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                //По умолчанию все эндпоинты требуют токен, публичные помечаются @SecurityRequirements() без аргументов
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
