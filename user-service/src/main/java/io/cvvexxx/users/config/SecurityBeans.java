package io.cvvexxx.users.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class SecurityBeans {

    //Основной бин конфигурации для Spring Security, определяет цепочку фильтров, которые проходит запрос перед тем,
    //как дойти до самого контроллера
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        request -> request
                                .requestMatchers("/api/users/register").permitAll()//Разрешаем доступ к эндпоинту регистрации всем пользователям
                                .anyRequest().authenticated()//Для доступа к остальным эндпоинтам юзер должен быть аутентифицирован, иначе 403
                )
                //Сессия нигде не хранится, все взаимодействие идет через JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //Дает понять, что наш микросервис является resourceServer'ом в контексте OAuth2
                //(Рекомендую про это посмотреть/прочитать, там же узнаете что такое Identity Provider(каковым Keycloak и является))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwtConfigurer ->
                                jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter())))//Описание правил, по которым парсить JWT
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> groups = extractGroups(jwt);
            List<String> realmRoles = extractRealmRoles(jwt);

            return Stream.concat(groups.stream(), realmRoles.stream())
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });

        return converter;
    }

    private List<String> extractGroups(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsStringList("groups"))
                .orElseGet(List::of);
    }

    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }

        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        return List.of();
    }

}
