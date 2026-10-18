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

@Configuration//Класс конфигурации, в котором создаем кастомные бины(через методы с @Bean)
@EnableConfigurationProperties(OtpProperties.class) //Регистрирует OtpProperties как бин, чтобы его можно было внедрять (DefaultOtpService)
public class OtpBeans {

    //Смотрит поле app.otp.store, по дефолту создает RedisOtpStore
    //Сделано для того, чтобы можно было поменять реализацию хранилища токенов без переписывания половины проекта
    @Bean
    @ConditionalOnProperty(name = "app.otp.store", havingValue = "redis", matchIfMissing = true) //if settings.otp_store == "redis" или не задано
    public OtpStore redisOtpStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisOtpStore(stringRedisTemplate);
    }

    //Создает inMemory хранилище(для тестов)
    @Bean
    @ConditionalOnProperty(name = "app.otp.store", havingValue = "memory") //Создается только при app.otp.store=memory
    public OtpStore inMemoryOtpStore() {
        return new InMemoryOtpStore();
    }

    //Создает LoggingSmsSender если в контексте нет иной реализации интерфейса SmsSender
    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender loggingSmsSender() {
        return new LoggingSmsSender();
    }
}
