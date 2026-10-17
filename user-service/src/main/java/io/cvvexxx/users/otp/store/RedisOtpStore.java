package io.cvvexxx.users.otp.store;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class RedisOtpStore implements OtpStore {

    private static final String CODE_KEY_PREFIX = "otp:code:";
    private static final String PERMIT_KEY_PREFIX = "otp:permit:";
    private static final String WINDOW_KEY_PREFIX = "otp:window:";

    private static final RedisScript<Boolean> SAVE_SCRIPT = RedisScript.of("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'expiresAt', ARGV[2], 'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Boolean.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> VERIFY_SCRIPT = RedisScript.of("""
            local stored = redis.call('HGET', KEYS[1], 'code')
            if not stored then
                return {'NOT_REQUESTED', '0'}
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return {'OK', '0'}
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            local left = tonumber(ARGV[2]) - attempts
            if left <= 0 then
                redis.call('DEL', KEYS[1])
                return {'ATTEMPTS_EXCEEDED', '0'}
            end
            return {'MISMATCH', tostring(left)}
            """, List.class);

    private static final RedisScript<Long> WINDOW_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    @Override
    public void save(String phone, String code, Duration ttl) {
        redis.execute(SAVE_SCRIPT,
                List.of(codeKey(phone)),
                code,
                String.valueOf(Instant.now().plus(ttl).toEpochMilli()),
                String.valueOf(ttl.toMillis()));
    }

    @Override
    public OtpVerification verify(String phone, String code, int maxAttempts) {
        List<?> result = redis.execute(VERIFY_SCRIPT,
                List.of(codeKey(phone)),
                code,
                String.valueOf(maxAttempts));

        if (result == null || result.size() < 2) {
            return OtpVerification.notRequested();
        }

        OtpVerification.Outcome outcome = OtpVerification.Outcome.valueOf(String.valueOf(result.get(0)));
        int attemptsLeft = Integer.parseInt(String.valueOf(result.get(1)));
        return new OtpVerification(outcome, attemptsLeft);
    }

    @Override
    public boolean tryAcquireSendPermit(String phone, Duration cooldown) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(permitKey(phone), "1", cooldown));
    }

    @Override
    public Duration sendPermitTimeToLive(String phone) {
        Long seconds = redis.getExpire(permitKey(phone));
        return seconds == null || seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    @Override
    public long incrementSendsInWindow(String phone, Duration window) {
        Long count = redis.execute(WINDOW_SCRIPT,
                List.of(windowKey(phone)),
                String.valueOf(window.toMillis()));
        return count == null ? 0 : count;
    }

    private String codeKey(String phone) {
        return CODE_KEY_PREFIX + phone;
    }

    private String permitKey(String phone) {
        return PERMIT_KEY_PREFIX + phone;
    }

    private String windowKey(String phone) {
        return WINDOW_KEY_PREFIX + phone;
    }
}
