package io.cvvexxx.billings.config;


import io.cvvexxx.billings.payment.provider.yookassa.YooKassaWebhookController;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

//Все запросы только с JWT от user-service, кроме Swagger
@Configuration
public class SecurityBeans {

    //Межсервисные эндпоинты
    @Order(1)
    @Bean
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity httpSecurity,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) {
        return httpSecurity
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(request ->
                        request.anyRequest().hasRole("SERVICE"))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .jwt(jwtConfigurer ->
                                        jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler)
                )
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .build();
    }


    //Пользовательские эндпоинты
    @Order(2)
    @Bean
    public SecurityFilterChain userSecurityFilterChain(
            HttpSecurity httpSecurity,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)//CSRF не нужен: cookie-сессий нет, токен приходит в заголовке
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, YooKassaWebhookController.PATH).permitAll()//Webhook ЮKassa приходит без JWT
                        .anyRequest().hasAnyRole("USER", "ADMIN")
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))//Сессии не храним, каждый запрос проверяется по токену
                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .jwt(jwtConfigurer ->
                                        jwtConfigurer
                                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                                )
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(
                        exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
                )
                .build();
    }

    //Роли из claim "roles" превращаем в ROLE_USER/ROLE_ADMIN, чтобы работал hasRole(...)
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
                    .orElseGet(List::of);

            return roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });

        return converter;
    }

    //401 и 403 отдаём через GlobalExceptionHandler, чтобы ответ был в ProblemDetail
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, e) -> {
            bearer.commence(request, response, e);
            handlerExceptionResolver.resolveException(request, response, null, e);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (request, response, e) -> {
            bearer.handle(request, response, e);
            handlerExceptionResolver.resolveException(request, response, null, e);
        };
    }

}
