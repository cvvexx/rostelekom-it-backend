package io.cvvexxx.billings.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

//Включает @Scheduled-джобы. В тестах выключено (app.scheduling.enabled=false), джобы там вызываются напрямую
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingBeans {
}
