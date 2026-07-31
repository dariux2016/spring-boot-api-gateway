package com.example.gateway.filter;

import java.time.Duration;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class RedisTokenBucketRateLimiter implements TokenBucketRateLimiter {

    private static final int CAPACITY = 10;
    private static final Duration REFILL_INTERVAL = Duration.ofMinutes(1);
    private static final int TOKENS_PER_INTERVAL = CAPACITY;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tokenBucketScript;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = buildScript();
    }

    @Override
    public Mono<Boolean> allow(String key) {
        return Mono.fromCallable(() -> {
            String bucketKey = "rate-limit:" + key;
            long now = System.currentTimeMillis();
            Long allowed = redisTemplate.execute(tokenBucketScript, Collections.singletonList(bucketKey),
                    String.valueOf(now), String.valueOf(CAPACITY), String.valueOf(TOKENS_PER_INTERVAL),
                    String.valueOf(REFILL_INTERVAL.toMillis()));
            return allowed != null && allowed == 1L;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private RedisScript<Long> buildScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local capacity = tonumber(ARGV[2])
                local tokensPerInterval = tonumber(ARGV[3])
                local refillInterval = tonumber(ARGV[4])

                local tokens = tonumber(redis.call('hget', key, 'tokens'))
                local lastRefill = tonumber(redis.call('hget', key, 'last_refill'))

                if tokens == nil then
                    tokens = capacity
                end

                if lastRefill == nil then
                    lastRefill = now
                end

                local elapsed = now - lastRefill
                local refill = math.floor(elapsed / refillInterval) * tokensPerInterval
                if refill > 0 then
                    tokens = math.min(capacity, tokens + refill)
                    lastRefill = lastRefill + (math.floor(elapsed / refillInterval) * refillInterval)
                end

                local allowed = 0
                if tokens > 0 then
                    tokens = tokens - 1
                    allowed = 1
                end

                redis.call('hset', key, 'tokens', tokens, 'last_refill', lastRefill)
                return allowed
                """);
        script.setResultType(Long.class);
        return script;
    }
}
