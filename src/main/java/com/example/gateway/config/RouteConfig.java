package com.example.gateway.config;

import java.time.Duration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    private final GatewayRoutesProperties gatewayProperties;

    public RouteConfig(GatewayRoutesProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routesBuilder = builder.routes();

        gatewayProperties.getRoutes().forEach((routeName, routeConfig) -> routesBuilder.route(routeName, r -> r.path(routeConfig.getPathPattern())
                .filters(f -> f.rewritePath(routeConfig.getRewritePattern(), routeConfig.getReplacement())
                        .addResponseHeader(
                                gatewayProperties.getObservability().getResponseHeaderName(),
                                routeConfig.getResponseHeaderValue())
                        .retry(config -> config.setRetries(routeConfig.getRetry().getRetries())
                                .setBackoff(
                                        Duration.ofMillis(routeConfig.getRetry().getInitialBackoffMs()),
                                        Duration.ofMillis(routeConfig.getRetry().getMaxBackoffMs()),
                                        routeConfig.getRetry().getFactor(),
                                        routeConfig.getRetry().isJitter()))
                        .circuitBreaker(config -> config.setName(routeConfig.getCircuitBreaker().getName())
                                .setFallbackUri(routeConfig.getCircuitBreaker().getFallbackUri())))
                .uri(routeConfig.getUri())));

        return routesBuilder.build();
    }
}
