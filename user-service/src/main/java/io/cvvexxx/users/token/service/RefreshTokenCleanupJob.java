package io.cvvexxx.users.token.service;

import io.cvvexxx.users.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component //Обычный бин (то же что @Service, просто не про бизнес-логику), без него @Scheduled не заработает
@RequiredArgsConstructor //Через конструктор приходит RefreshTokenRepository
@Slf4j //Создает поле log
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    //Раз в fixedDelayString просыпается этот метод и очищает все истекшие refresh токены
    //${app.jwt.cleanup-interval:PT1H} - значение из .properties, если его нет, то PT1H(1 час)
    //fixedDelay - пауза отсчитывается после окончания прошлого запуска
    @Scheduled(fixedDelayString = "${app.jwt.cleanup-interval:PT1H}")
    @Transactional //Нужна для @Modifying-запроса delete
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
