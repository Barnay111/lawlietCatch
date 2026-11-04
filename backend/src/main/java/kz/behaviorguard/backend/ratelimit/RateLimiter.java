package kz.behaviorguard.backend.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Фиксированное окно в одну минуту на ключ, общее для всех инстансов через Redis.
 * Если Redis недоступен: вход закрывается (fail closed), API пропускается (fail open) с записью в лог.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String PREFIX = "rl:";

    /** Исход проверки. UNAVAILABLE — Redis не ответил. */
    public enum Result { ALLOWED, DENIED, UNAVAILABLE }

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static long currentMinute(long nowMs) {
        return nowMs / 60_000L;
    }

    public Result tryAcquire(String key, int limitPerMinute, long nowMs) {
        String windowKey = PREFIX + key + ":" + currentMinute(nowMs);
        try {
            Long n = redis.opsForValue().increment(windowKey);
            if (n != null && n == 1L) {
                redis.expire(windowKey, Duration.ofMinutes(2));
            }
            return (n != null && n <= limitPerMinute) ? Result.ALLOWED : Result.DENIED;
        } catch (DataAccessException e) {
            log.warn("Redis недоступен для ограничения частоты: {}", e.getMessage());
            return Result.UNAVAILABLE;
        }
    }

    /** Секунд до конца текущей минуты, для Retry-After. */
    public long retryAfterSeconds(long nowMs) {
        return Math.max(1, 60 - (nowMs / 1000) % 60);
    }
}
