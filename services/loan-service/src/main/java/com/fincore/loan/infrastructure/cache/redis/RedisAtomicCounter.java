package com.fincore.loan.infrastructure.cache.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Non-financial Redis atomic-counter example; never used as a monetary source of truth. */
@Component
@RequiredArgsConstructor
public class RedisAtomicCounter {

    private static final String KEY_PREFIX = "fincore:metrics:counter:";

    private final StringRedisTemplate redisTemplate;

    public long increment(String counterName) {
        if (counterName == null || counterName.isBlank()) {
            throw new IllegalArgumentException("counterName must not be blank");
        }
        Long value = redisTemplate.opsForValue().increment(KEY_PREFIX + counterName);
        return value == null ? 0 : value;
    }
}
