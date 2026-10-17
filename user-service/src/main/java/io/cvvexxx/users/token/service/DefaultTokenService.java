package io.cvvexxx.users.token.service;

import io.cvvexxx.users.token.config.JwtProperties;
import io.cvvexxx.users.token.dto.TokenPairDto;
import io.cvvexxx.users.token.entity.RefreshToken;
import io.cvvexxx.users.token.exception.InvalidRefreshTokenException;
import io.cvvexxx.users.token.repository.RefreshTokenRepository;
import io.cvvexxx.users.user.entity.Role;
import io.cvvexxx.users.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTokenService implements TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public TokenPairDto issueTokens(User user) {
        Instant now = Instant.now();

        String accessToken = buildAccessToken(user, now);
        String refreshToken = buildRefreshToken(user, now);

        return new TokenPairDto(
                accessToken,
                refreshToken,
                TokenPairDto.BEARER,
                jwtProperties.accessTokenTtl().toSeconds(),
                user.getId()
        );
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPairDto refresh(String refreshToken) {
        Instant now = Instant.now();

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is unknown"));

        if (stored.getRevokedAt() != null) {
            log.warn("Reuse of a revoked refresh token detected for user {}, revoking all sessions",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId(), now);
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (!stored.isActive(now)) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        stored.setRevokedAt(now);

        User user = stored.getUser();
        return new TokenPairDto(
                buildAccessToken(user, now),
                buildRefreshToken(user, now),
                TokenPairDto.BEARER,
                jwtProperties.accessTokenTtl().toSeconds(),
                user.getId()
        );
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private String buildAccessToken(User user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("phone", user.getPhone())
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String buildRefreshToken(User user, Instant now) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.refreshTokenTtl()))
                .build());

        return token;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
