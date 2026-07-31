package com.example.gateway.filter;

import reactor.core.publisher.Mono;

public interface TokenBucketRateLimiter {
    Mono<Boolean> allow(String key);
}
