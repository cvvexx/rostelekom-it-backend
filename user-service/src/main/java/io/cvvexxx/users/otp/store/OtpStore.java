package io.cvvexxx.users.otp.store;

import java.time.Duration;

public interface OtpStore {

    void save(String phone, String code, Duration ttl);

    OtpVerification verify(String phone, String code, int maxAttempts);

    boolean tryAcquireSendPermit(String phone, Duration cooldown);

    Duration sendPermitTimeToLive(String phone);

    long incrementSendsInWindow(String phone, Duration window);

    Duration sendsWindowTimeToLive(String phone);
}
