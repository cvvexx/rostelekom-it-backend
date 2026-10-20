package io.cvvexxx.billings.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter = new SecurityBeans().jwtAuthenticationConverter();

    @Test
    @DisplayName("Роли из claim roles получают префикс ROLE_, пустые и дубли отбрасываются")
    void rolesClaimIsMappedToAuthorities() {
        AbstractAuthenticationToken authentication = converter.convert(jwt(List.of("USER", "ROLE_ADMIN", " ", "USER")));

        assertThat(roles(authentication)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Нет claim roles — пустой список ролей, а не NPE")
    void missingRolesClaim_givesNoRoles() {
        AbstractAuthenticationToken authentication = converter.convert(jwt(null));

        assertThat(roles(authentication)).isEmpty();
    }

    @Test
    @DisplayName("Имя аутентификации — subject токена (id пользователя)")
    void principalNameIsSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(userId.toString()).build();

        assertThat(converter.convert(jwt).getName()).isEqualTo(userId.toString());
    }

    //Spring Security 7 сам добавляет FACTOR_BEARER, нас интересуют только роли
    private List<String> roles(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .toList();
    }

    private Jwt jwt(List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString());
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }
}
