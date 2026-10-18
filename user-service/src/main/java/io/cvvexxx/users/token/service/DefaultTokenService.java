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

@Service //Бин, Spring создаст один объект и подставит его в DefaultAuthService
@RequiredArgsConstructor //Через конструктор приходят JwtEncoder, JwtProperties, RefreshTokenRepository
@Slf4j //Создает поле log
public class DefaultTokenService implements TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional //Сохранение refresh-токена в БД идет в транзакции
    public TokenPairDto issueTokens(User user) {
        Instant now = Instant.now();//Получаем текущее время

        String accessToken = buildAccessToken(user, now);//Создаем access токены
        String refreshToken = buildRefreshToken(user, now);//Создаем refresh токен

        return new TokenPairDto(
                accessToken,
                refreshToken,
                TokenPairDto.BEARER,
                jwtProperties.accessTokenTtl().toSeconds(),
                user.getId()
        );
    }

    @Override
    //noRollbackFor стоит для того, чтобы если токен перехватил злоумышленник, то у нас записались данные в бд
    //и не откатились из-за выброшенного исключения
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPairDto refresh(String refreshToken) {
        Instant now = Instant.now();

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is unknown"));

        //Если токен был отменен до этого, то значит что использует уже отключенный токен ->
        //значит токен получил злоумышленник, в таком случае в целях безопасности завершаем все сессии юзера
        if (stored.getRevokedAt() != null) {
            log.warn("Reuse of a revoked refresh token detected for user {}, revoking all sessions",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId(), now);//завершаем все сессии юзера
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }

        if (!stored.isActive(now)) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        stored.setRevokedAt(now);//Ставим что токен уже отозван и мы поменяли его на новый

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
    //Изменение revokedAt сохранится в БД само при коммите транзакции, без явного save() -
    //Hibernate отслеживает изменения загруженных сущностей (похоже на session в SQLAlchemy)
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));//Отзываем refresh токен при logout'е
    }

    private String buildAccessToken(User user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                //ТОКЕН МОЖЕТЕ РАСПАРСИТЬ на сайте JWT.IO, чтобы посмотреть че в него входит

                //Тот кто выпустил токен(user-service) в дев это localhost
                .issuer(jwtProperties.issuer())
                //Те, для кого выпускаются токены, ща rostelekom
                .audience(List.of(jwtProperties.audience()))
                //sub - айди юзера
                .subject(user.getId().toString())
                //Выпущен в
                .issuedAt(now)
                //Истекает в
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                //Уникальный id токена
                .id(UUID.randomUUID().toString())
                //Поле в токене, телефон юзера
                .claim("phone", user.getPhone())
                //Поле в токене, роли юзера списком
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .build();

        //Алгоритм подписи токена для его последующей проверки на подлинность
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String buildRefreshToken(User user, Instant now) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        //Сохраняем токен в базу данных
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))//Храним хэш токена в соображениях безопасности
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.refreshTokenTtl()))
                .build());

        return token;
    }

    //Считает хэш для последующего сохранения в бд
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
