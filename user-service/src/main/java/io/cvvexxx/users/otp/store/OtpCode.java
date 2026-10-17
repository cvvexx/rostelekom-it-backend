package io.cvvexxx.users.otp.store;

import java.time.Instant;

public record OtpCode(
        String code,
        Instant expiresAt,
        int attempts
) {
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
