package io.cvvexxx.users.otp.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

//InMemory-хранилище для тестов
public class InMemoryOtpStore implements OtpStore {

    private record Permit(Instant expiresAt) {
    }

    private record Window(Instant expiresAt, long count) {
    }

    private final Map<String, OtpCode> codes = new ConcurrentHashMap<>();
    private final Map<String, Permit> permits = new ConcurrentHashMap<>();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public void save(String phone, String code, Duration ttl) {
        codes.put(phone, new OtpCode(code, Instant.now().plus(ttl), 0));
    }

    @Override
    public OtpVerification verify(String phone, String code, int maxAttempts) {
        Instant now = Instant.now();
        AtomicReference<OtpVerification> result = new AtomicReference<>();

        codes.compute(phone, (key, stored) -> {
            if (stored == null || stored.isExpired(now)) {
                result.set(OtpVerification.notRequested());
                return null;
            }

            if (matches(stored.code(), code)) {
                result.set(OtpVerification.ok());
                return null;
            }

            int attempts = stored.attempts() + 1;
            int attemptsLeft = maxAttempts - attempts;
            if (attemptsLeft <= 0) {
                result.set(OtpVerification.attemptsExceeded());
                return null;
            }

            result.set(OtpVerification.mismatch(attemptsLeft));
            return new OtpCode(stored.code(), stored.expiresAt(), attempts);
        });

        return result.get();
    }

    @Override
    public boolean tryAcquireSendPermit(String phone, Duration cooldown) {
        Instant now = Instant.now();
        Permit fresh = new Permit(now.plus(cooldown));

        Permit current = permits.compute(phone, (key, existing) ->
                existing != null && existing.expiresAt().isAfter(now) ? existing : fresh);

        return current == fresh;
    }

    @Override
    public Duration sendPermitTimeToLive(String phone) {
        Permit permit = permits.get(phone);
        if (permit == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(Instant.now(), permit.expiresAt());
        return left.isNegative() ? Duration.ZERO : left;
    }

    @Override
    public void releaseSendPermit(String phone) {
        permits.remove(phone);
    }

    @Override
    public long incrementSendsInWindow(String phone, Duration window) {
        Instant now = Instant.now();
        Window updated = windows.compute(phone, (key, current) ->
                current == null || !current.expiresAt().isAfter(now)
                        ? new Window(now.plus(window), 1)
                        : new Window(current.expiresAt(), current.count() + 1));
        return updated.count();
    }

    @Override
    public Duration sendsWindowTimeToLive(String phone) {
        Window window = windows.get(phone);
        if (window == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(Instant.now(), window.expiresAt());
        return left.isNegative() ? Duration.ZERO : left;
    }

    public Optional<String> peekCode(String phone) {
        OtpCode stored = codes.get(phone);
        if (stored == null || stored.isExpired(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(stored.code());
    }

    public void clear() {
        codes.clear();
        permits.clear();
        windows.clear();
    }

    private boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
