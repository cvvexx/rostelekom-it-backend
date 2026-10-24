package io.cvvexxx.billings.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter = new SecurityBeans().jwtAuthenticationConverter();

    @Test
    @DisplayName("Роли из claim roles получают префикс ROLE_, пустые и дубли отбрасываются")
    void rolesClaimIsMappedToAuthorities() {
        assertThat(roles(Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("roles", List.of("USER", "ROLE_ADMIN", " ", "USER")).build()))
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Нет claim roles — пустой список ролей, а не NPE")
    void missingRolesClaim_givesNoRoles() {
        assertThat(roles(Jwt.withTokenValue("t").header("alg", "RS256").subject("u").build())).isEmpty();
    }

    //Spring Security 7 сам добавляет FACTOR_BEARER, нас интересуют только роли
    private List<String> roles(Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .toList();
    }
}
