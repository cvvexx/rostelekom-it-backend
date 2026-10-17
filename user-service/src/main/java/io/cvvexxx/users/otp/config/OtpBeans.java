package io.cvvexxx.users.otp.config;

import io.cvvexxx.users.otp.store.InMemoryOtpStore;
import io.cvvexxx.users.otp.store.OtpStore;
import io.cvvexxx.users.otp.store.RedisOtpStore;
import io.cvvexxx.users.sms.LoggingSmsSender;
import io.cvvexxx.users.sms.SmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(OtpProperties.class)
public class OtpBeans {

    @Bean
    @ConditionalOnProperty(name = "app.otp.store", havingValue = "redis", matchIfMissing = true)
    public OtpStore redisOtpStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisOtpStore(stringRedisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "app.otp.store", havingValue = "memory")
    public OtpStore inMemoryOtpStore() {
        return new InMemoryOtpStore();
    }

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender loggingSmsSender() {
        return new LoggingSmsSender();
    }
}
