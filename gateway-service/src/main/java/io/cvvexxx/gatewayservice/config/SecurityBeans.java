package io.cvvexxx.gatewayservice.config;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;

@Configuration //Класс-конфиг: при старте Spring вызовет все @Bean-методы внутри и сохранит их результаты
public class SecurityBeans {

    //@Bean - метод-фабрика: Spring вызывает его один раз при старте, кладет результат в контейнер и подставляет
    //туда, где этот тип нужен (как функция для Depends() в FastAPI, только объект один на все приложение).
    //Параметры метода (HttpSecurity, обработчики ниже) Spring тоже находит и подставляет сам
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            StringRedisTemplate redis,
            @Value("${rostelekom.rate-limit.capacity}") long rateLimitCapacity,
            @Value("${rostelekom.rate-limit.window}") Duration rateLimitWindow,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        request -> request
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/fallback/**", "/error").permitAll()
                                .anyRequest().authenticated()
                )
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                //Не через @Component, иначе Spring зарегистрирует фильтр ещё и вне security и он сработает дважды
                .addFilterAfter(
                        new RateLimitFilter(redis, rateLimitCapacity, rateLimitWindow, handlerExceptionResolver),
                        BearerTokenAuthenticationFilter.class
                )
                .exceptionHandling(
                        exceptions -> exceptions
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    @Bean //Обработчик 401, Spring сам подставит его в securityFilterChain выше
    public AuthenticationEntryPoint authenticationEntryPoint(
            //Бинов типа HandlerExceptionResolver в Spring несколько, поэтому через @Qualifier явно говорим, какой взять(по имени)
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return new ProblemDetailAuthenticationEntryPoint(handlerExceptionResolver);
    }

    @Bean //Обработчик 403, аналогично тому, что сверху
    public AccessDeniedHandler accessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return new ProblemDetailAccessDeniedHandler(handlerExceptionResolver);
    }

}
