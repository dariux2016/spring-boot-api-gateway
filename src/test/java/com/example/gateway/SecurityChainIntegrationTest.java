package com.example.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.example.gateway.filter.TokenBucketRateLimiter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

/**
 * Verifies end-to-end that JWT-authenticated requests actually pass Spring Security's
 * WebFilterChain, and that requests without a JWT are rejected by
 * {@link com.example.gateway.filter.JwtAuthenticationFilter} itself (not left unauthenticated
 * by a permissive Security chain).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SecurityChainIntegrationTest {

    private static final String TEST_SECRET = "test-secret-key-1234567890-abcdefghijklmnopqrstuvwxyz-123456";

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> TEST_SECRET);
        // Fails fast on loopback instead of depending on external/placeholder DNS.
        registry.add("gateway.routes.secure.uri", () -> "http://127.0.0.1:1");
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String validToken() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Jwts.builder()
                .setSubject("user-123")
                .signWith(Keys.hmacShaKeyFor(digest.digest(TEST_SECRET.getBytes(StandardCharsets.UTF_8))))
                .compact();
    }

    @Test
    void rejectsRequestWithoutJwtBeforeRouting() {
        client().get().uri("/secure/ping")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsRequestWithValidJwtPastSecurityChain() throws NoSuchAlgorithmException {
        // Backend is unreachable (loopback, nothing listening), so a valid JWT should pass the
        // Security chain and JWT filter, exhaust retries, trip the circuit breaker, and land on
        // the fallback controller (503) -- never a 401 from either auth layer.
        client().get().uri("/secure/ping")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void permitsActuatorHealthWithoutAuthentication() {
        // Redis isn't running in this test, so the health aggregator may report DOWN (503);
        // what matters here is that the Security chain doesn't block it with 401/403.
        client().get().uri("/actuator/health")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                        throw new AssertionError("actuator/health should not require authentication, got " + status);
                    }
                });
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TokenBucketRateLimiter alwaysAllowRateLimiter() {
            return key -> Mono.just(true);
        }
    }
}
