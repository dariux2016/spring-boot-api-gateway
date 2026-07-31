package com.example.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-1234567890-abcdefghijklmnopqrstuvwxyz-123456";

    @Test
    void injectsClaimsAsHeadersWhenTokenIsValid() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET, "Authorization");
        String token = Jwts.builder()
                .setSubject("user-123")
                .claim("name", "Ada")
                .claim("email", "ada@example.com")
                .claim("roles", new String[] {"USER", "ADMIN"})
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(buildSigningKey(TEST_SECRET), SignatureAlgorithm.HS256)
                .compact();

        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> userName = new AtomicReference<>();
        AtomicReference<String> roles = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            userId.set(exchange.getRequest().getHeaders().getFirst("X-User-Id"));
            userName.set(exchange.getRequest().getHeaders().getFirst("X-User-Name"));
            roles.set(exchange.getRequest().getHeaders().getFirst("X-User-Roles"));
            return Mono.empty();
        };

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/products")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(userId.get()).isEqualTo("user-123");
        assertThat(userName.get()).isEqualTo("Ada");
        assertThat(roles.get()).isEqualTo("USER,ADMIN");
    }

    private javax.crypto.SecretKey buildSigningKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Keys.hmacShaKeyFor(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    @Test
    void returnsUnauthorizedForInvalidToken() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(TEST_SECRET, "Authorization");
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/products")
                        .header("Authorization", "Bearer invalid-token")
                        .build());

        filter.filter(exchange, exchange1 -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
