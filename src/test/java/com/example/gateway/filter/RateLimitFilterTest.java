package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class RateLimitFilterTest {

    @Test
    void returnsTooManyRequestsWhenTokenBucketRejectsRequest() {
        TokenBucketRateLimiter limiter = key -> Mono.just(false);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, exchange1 -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void continuesChainWhenTokenBucketAllowsRequest() {
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        TokenBucketRateLimiter limiter = key -> Mono.just(true);
        RateLimitFilter filter = new RateLimitFilter(limiter);

        GatewayFilterChain chain = exchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());

        filter.filter(exchange, chain).block();

        assertThat(chainInvoked).isTrue();
    }
}
