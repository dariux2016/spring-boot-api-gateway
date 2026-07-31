package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class ObservabilityWebFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsCorrelationIdHeaderAndMdcContext() {
        ObservabilityWebFilter filter = new ObservabilityWebFilter(null);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        AtomicReference<ServerWebExchange> mutatedExchange = new AtomicReference<>();

        WebFilterChain chain = innerExchange -> {
            mutatedExchange.set(innerExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(mutatedExchange.get()).isNotNull();
        assertThat(mutatedExchange.get().getRequest().getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
        assertThat(MDC.get("correlationId")).isEqualTo(mutatedExchange.get().getRequest().getHeaders().getFirst("X-Correlation-ID"));
        assertThat(MDC.get("traceId")).isEqualTo("none");
        assertThat(MDC.get("spanId")).isEqualTo("none");
    }
}
