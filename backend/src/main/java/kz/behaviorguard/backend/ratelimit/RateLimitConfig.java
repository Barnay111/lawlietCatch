package kz.behaviorguard.backend.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Лимиты в запросах в минуту. Значения задаются в application.properties и переопределяются через окружение. */
@Component
public class RateLimitConfig {

    private final int loginPerMinute;
    private final int apiPerMinute;

    public RateLimitConfig(@Value("${behaviorguard.ratelimit.login-per-minute:10}") int loginPerMinute,
                           @Value("${behaviorguard.ratelimit.api-per-minute:60000}") int apiPerMinute) {
        this.loginPerMinute = loginPerMinute;
        this.apiPerMinute = apiPerMinute;
    }

    public int loginPerMinute() { return loginPerMinute; }
    public int apiPerMinute() { return apiPerMinute; }
}
