package com.example.gateway.filter;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class ObservabilityWebFilter implements WebFilter, Ordered {

    private final String correlationHeaderName;

    public ObservabilityWebFilter() {
        this("X-Correlation-ID");
    }

    public ObservabilityWebFilter(String correlationHeaderName) {
        this.correlationHeaderName = correlationHeaderName == null || correlationHeaderName.isBlank()
                ? "X-Correlation-ID"
                : correlationHeaderName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String correlationId = exchange.getRequest().getHeaders().getFirst(correlationHeaderName);
        final String resolvedCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;

        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.set(correlationHeaderName, resolvedCorrelationId);
                return headers;
            }
        };

        MDC.put("correlationId", resolvedCorrelationId);
        MDC.put("traceId", MDC.get("traceId") == null ? "none" : MDC.get("traceId"));
        MDC.put("spanId", MDC.get("spanId") == null ? "none" : MDC.get("spanId"));

        ServerWebExchange mutatedExchange = new ServerWebExchangeDecorator(exchange) {
            @Override
            public ServerHttpRequest getRequest() {
                return mutatedRequest;
            }
        };

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
