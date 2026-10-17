package io.cvvexxx.users.otp.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfDockerAvailable
class RedisOtpStoreIT {

    private static final String PHONE = "+79991234567";
    private static final String CODE = "123456";
    private static final int MAX_ATTEMPTS = 5;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

    private static StringRedisTemplate redis;

    private RedisOtpStore store;

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @BeforeEach
    void flush() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        store = new RedisOtpStore(redis);
    }

    @Test
    @DisplayName("save выставляет TTL и обнуляет счётчик попыток")
    void saveSetsTtlAndResetsAttempts() {
        store.save(PHONE, CODE, Duration.ofMinutes(5));

        Long ttl = redis.getExpire("otp:code:" + PHONE);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(300);
        assertThat(redis.opsForHash().get("otp:code:" + PHONE, "attempts")).isEqualTo("0");

        store.verify(PHONE, "000000", MAX_ATTEMPTS);
        store.save(PHONE, CODE, Duration.ofMinutes(5));
        assertThat(redis.opsForHash().get("otp:code:" + PHONE, "attempts")).isEqualTo("0");
    }

    @Test
    @DisplayName("verify: верный код проходит и сжигает ключ")
    void verifyCorrectCodeBurnsKey() {
        store.save(PHONE, CODE, Duration.ofMinutes(5));

        assertThat(store.verify(PHONE, CODE, MAX_ATTEMPTS).outcome())
                .isEqualTo(OtpVerification.Outcome.OK);
        assertThat(redis.hasKey("otp:code:" + PHONE)).isFalse();
        assertThat(store.verify(PHONE, CODE, MAX_ATTEMPTS).outcome())
                .isEqualTo(OtpVerification.Outcome.NOT_REQUESTED);
    }

    @Test
    @DisplayName("verify: неверный код уменьшает остаток попыток, последняя сжигает ключ")
    void verifyWrongCodeCountsAttempts() {
        store.save(PHONE, CODE, Duration.ofMinutes(5));

        assertThat(store.verify(PHONE, "000000", MAX_ATTEMPTS))
                .isEqualTo(OtpVerification.mismatch(4));
        assertThat(store.verify(PHONE, "000000", MAX_ATTEMPTS))
                .isEqualTo(OtpVerification.mismatch(3));
        assertThat(store.verify(PHONE, "000000", MAX_ATTEMPTS))
                .isEqualTo(OtpVerification.mismatch(2));
        assertThat(store.verify(PHONE, "000000", MAX_ATTEMPTS))
                .isEqualTo(OtpVerification.mismatch(1));
        assertThat(store.verify(PHONE, "000000", MAX_ATTEMPTS).outcome())
                .isEqualTo(OtpVerification.Outcome.ATTEMPTS_EXCEEDED);

        assertThat(redis.hasKey("otp:code:" + PHONE)).isFalse();
        assertThat(store.verify(PHONE, CODE, MAX_ATTEMPTS).outcome())
                .isEqualTo(OtpVerification.Outcome.NOT_REQUESTED);
    }

    @Test
    @DisplayName("verify: 200 одновременных попыток не пробивают лимит в 5")
    void parallelGuessesCannotExceedAttemptLimit() throws Exception {
        store.save(PHONE, CODE, Duration.ofMinutes(5));

        int threads = 200;
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger mismatch = new AtomicInteger();
        AtomicInteger exceeded = new AtomicInteger();
        AtomicInteger notRequested = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            List<? extends Future<?>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        switch (store.verify(PHONE, "9%05d".formatted(i), MAX_ATTEMPTS).outcome()) {
                            case OK -> ok.incrementAndGet();
                            case MISMATCH -> mismatch.incrementAndGet();
                            case ATTEMPTS_EXCEEDED -> exceeded.incrementAndGet();
                            case NOT_REQUESTED -> notRequested.incrementAndGet();
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(ok.get()).isZero();
        assertThat(mismatch.get()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(exceeded.get()).isEqualTo(1);
        assertThat(notRequested.get()).isEqualTo(threads - MAX_ATTEMPTS);
        assertThat(redis.hasKey("otp:code:" + PHONE)).isFalse();
    }

    @Test
    @DisplayName("tryAcquireSendPermit: разрешение достаётся ровно одному из параллельных запросов")
    void sendPermitIsGrantedOnce() throws Exception {
        int threads = 50;
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<? extends Future<?>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        if (store.tryAcquireSendPermit(PHONE, Duration.ofSeconds(60))) {
                            granted.incrementAndGet();
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(granted.get()).isEqualTo(1);
        assertThat(store.sendPermitTimeToLive(PHONE)).isPositive();
    }

    @Test
    @DisplayName("incrementSendsInWindow: TTL ставится один раз от первой отправки")
    void windowTtlIsAnchoredToFirstSend() throws Exception {
        assertThat(store.incrementSendsInWindow(PHONE, Duration.ofSeconds(60))).isEqualTo(1);
        Long afterFirst = redis.getExpire("otp:window:" + PHONE);
        assertThat(afterFirst).isPositive();

        Thread.sleep(1100);

        assertThat(store.incrementSendsInWindow(PHONE, Duration.ofSeconds(60))).isEqualTo(2);
        assertThat(redis.getExpire("otp:window:" + PHONE)).isLessThan(afterFirst);
    }

    @Test
    @DisplayName("incrementSendsInWindow: параллельные отправки не теряют инкременты")
    void windowCounterDoesNotLoseIncrements() throws Exception {
        int threads = 100;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<? extends Future<?>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return store.incrementSendsInWindow(PHONE, Duration.ofSeconds(60));
                    }))
                    .toList();

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(redis.opsForValue().get("otp:window:" + PHONE)).isEqualTo(String.valueOf(threads));
        assertThat(redis.getExpire("otp:window:" + PHONE)).isPositive();
    }
}
