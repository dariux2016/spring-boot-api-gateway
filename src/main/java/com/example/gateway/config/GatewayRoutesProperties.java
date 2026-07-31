package com.example.gateway.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayRoutesProperties {

    private Observability observability = new Observability();
    private Map<String, RouteConfig> routes = new LinkedHashMap<>();

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability;
    }

    public Map<String, RouteConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RouteConfig> routes) {
        this.routes = routes;
    }

    public static class Observability {
        private String correlationHeaderName = "X-Correlation-ID";
        private String correlationHeaderValue = "gateway";
        private String responseHeaderName = "X-Gateway-Route";

        public String getCorrelationHeaderName() {
            return correlationHeaderName;
        }

        public void setCorrelationHeaderName(String correlationHeaderName) {
            this.correlationHeaderName = correlationHeaderName;
        }

        public String getCorrelationHeaderValue() {
            return correlationHeaderValue;
        }

        public void setCorrelationHeaderValue(String correlationHeaderValue) {
            this.correlationHeaderValue = correlationHeaderValue;
        }

        public String getResponseHeaderName() {
            return responseHeaderName;
        }

        public void setResponseHeaderName(String responseHeaderName) {
            this.responseHeaderName = responseHeaderName;
        }
    }

    public static class RouteConfig {
        private String uri;
        private String pathPattern;
        private String rewritePattern;
        private String replacement;
        private String responseHeaderValue;
        private RetryConfig retry = new RetryConfig();
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public String getRewritePattern() {
            return rewritePattern;
        }

        public void setRewritePattern(String rewritePattern) {
            this.rewritePattern = rewritePattern;
        }

        public String getReplacement() {
            return replacement;
        }

        public void setReplacement(String replacement) {
            this.replacement = replacement;
        }

        public String getResponseHeaderValue() {
            return responseHeaderValue;
        }

        public void setResponseHeaderValue(String responseHeaderValue) {
            this.responseHeaderValue = responseHeaderValue;
        }

        public RetryConfig getRetry() {
            return retry;
        }

        public void setRetry(RetryConfig retry) {
            this.retry = retry;
        }

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }
    }

    public static class RetryConfig {
        private int retries = 3;
        private long initialBackoffMs = 200;
        private long maxBackoffMs = 2000;
        private int factor = 2;
        private boolean jitter = true;

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }

        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }

        public int getFactor() {
            return factor;
        }

        public void setFactor(int factor) {
            this.factor = factor;
        }

        public boolean isJitter() {
            return jitter;
        }

        public void setJitter(boolean jitter) {
            this.jitter = jitter;
        }
    }

    public static class CircuitBreakerConfig {
        private String name;
        private String fallbackUri = "forward:/fallback";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFallbackUri() {
            return fallbackUri;
        }

        public void setFallbackUri(String fallbackUri) {
            this.fallbackUri = fallbackUri;
        }
    }
}
