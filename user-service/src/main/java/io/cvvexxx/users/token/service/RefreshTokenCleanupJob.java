package io.cvvexxx.users.token.service;

import io.cvvexxx.users.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    //Раз в fixedDelayString просыпается этот метод и очищает все истекшие refresh токены
    @Scheduled(fixedDelayString = "${app.jwt.cleanup-interval:PT1H}")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}
