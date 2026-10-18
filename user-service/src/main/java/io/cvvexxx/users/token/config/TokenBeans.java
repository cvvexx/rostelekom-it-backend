package io.cvvexxx.users.token.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.pem.PemContent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

//Класс конфигурации в котором создаются бины для токенов
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@Slf4j
public class TokenBeans {

    private static final int GENERATED_KEY_SIZE = 2048;

    //Ключ подписи токена, для проверки валидности
    @Bean
    public RSAKey rsaKey(JwtProperties properties, ResourceLoader resourceLoader) {
        RSAPrivateKey privateKey = loadOrGeneratePrivateKey(properties, resourceLoader);
        RSAPublicKey publicKey = derivePublicKey(privateKey);

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    //Бин для проверки подлинности токенов
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, JwtProperties properties) {
        RSAPublicKey publicKey;
        try {
            publicKey = rsaKey.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to read RSA public key", e);
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(properties.issuer()),
                new AudienceValidator(properties.audience())));

        return decoder;
    }

    private RSAPrivateKey loadOrGeneratePrivateKey(JwtProperties properties, ResourceLoader resourceLoader) {
        String location = properties.privateKeyLocation();

        if (location == null || location.isBlank()) {//TODO(Сделать один приватный ключ токенов и убрать его в .env)
            log.warn("app.jwt.private-key-location is not set, generating an ephemeral RSA key pair. "
                    + "All issued access tokens become invalid after restart. Do not use this in production.");
            return (RSAPrivateKey) generateKeyPair().getPrivate();
        }

        Resource resource = resourceLoader.getResource(location);
        try {
            String pem = resource.getContentAsString(StandardCharsets.UTF_8);
            PrivateKey privateKey = PemContent.of(pem).getPrivateKey();
            if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)) {
                throw new IllegalStateException("Expected an RSA private key at " + location);
            }
            return rsaPrivateKey;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JWT private key from " + location, e);
        }
    }

    private RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException("RSA private key must be in CRT form to derive the public key");
        }

        BigInteger modulus = crtKey.getModulus();
        BigInteger publicExponent = crtKey.getPublicExponent();
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to derive RSA public key", e);
        }
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation is not available", e);
        }
    }
}
