package io.cvvexxx.users.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
public class SecurityBeans {

    //Основной бин конфигурации для Spring Security, определяет цепочку фильтров, которые проходит запрос перед тем,
    //как дойти до самого контроллера
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        request -> request
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                                //Для доступа к остальным эндпоинтам юзер должен быть аутентифицирован
                                .anyRequest().authenticated()
                )
                //Сессия нигде не хранится, все взаимодействие идет через JWT
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .jwt(jwtConfigurer ->
                                        //Конвертер достает роли из токена связывает их с ролями Spring Security
                                        jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter())
                                )
                                .authenticationEntryPoint(authenticationEntryPoint)//При 401 вызывает обработчик ошибок, описанный ниже в @Bean
                                .accessDeniedHandler(accessDeniedHandler))//Аналогично тому, что сверху
                .exceptionHandling(
                        exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
                )
                .build();
    }


    //Создаем обработчик ошибки 401
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return new ProblemDetailAuthenticationEntryPoint(handlerExceptionResolver);
    }

    //Создаем обработчик ошибки 403
    @Bean
    public AccessDeniedHandler accessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return new ProblemDetailAccessDeniedHandler(handlerExceptionResolver);
    }

    //Конвертер JWT в роли
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

}
