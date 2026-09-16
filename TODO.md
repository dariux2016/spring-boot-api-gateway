# TODO

This file tracks known limitations, improvements, and open verifications identified during a full codebase review. Items are grouped by category and roughly ordered by severity within each group.

## Critical

- [x] `jwt.secret` (`${JWT_SECRET:}`) defaults to an empty string when the environment variable is not set, producing a predictable HMAC signing key (SHA-256 of an empty string). Startup should fail fast instead when the secret is missing or empty. Fixed: `JwtAuthenticationFilter` now throws `IllegalStateException` at construction time if the secret is null/blank.
- [ ] The JWT authentication filter (`JwtAuthenticationFilter`, a Gateway `GlobalFilter`) never populates the `ReactiveSecurityContextHolder`, while `SecurityConfig` requires `.anyExchange().authenticated()` with `httpBasic()` as the only configured mechanism (no `UserDetailsService`). The two authentication layers appear uncoordinated — verify end-to-end whether JWT-authenticated requests actually pass the Spring Security chain, then reconcile the two (either wire the JWT filter into the reactive security context, or remove the unused `httpBasic()` fallback).
- [ ] Configured route URIs (`http://products-service`, `http://orders-service`, `http://secure-service`) are placeholder hostnames with no service discovery or `lb://` load-balancer scheme — the gateway is not wired to any real backend today.

## Security

- [ ] JWT validation only checks signature and expiration (via JJWT defaults) — no `issuer`/`audience` validation, no revocation/blacklist mechanism, no explicit signing-algorithm allow-list.
- [ ] Authentication uses a symmetric HMAC secret shared between token issuer and gateway, rather than asymmetric verification via a JWKS endpoint (typical OIDC resource-server model). Consider `spring-boot-starter-oauth2-resource-server` if an asymmetric/OIDC model is desired.
- [ ] Rate limiting keys on `exchange.getRequest().getRemoteAddress()` and ignores `X-Forwarded-For`, so all clients behind a reverse proxy or load balancer share one bucket.
- [ ] No role-based authorization at the gateway level — roles extracted from the JWT are only forwarded as an `X-User-Roles` header for downstream services to interpret; there is no per-route RBAC.
- [ ] No security response headers are added (HSTS, X-Content-Type-Options, CSP, etc.).
- [ ] `spring.cloud.gateway.server.webflux.httpclient.wiretap` / `httpserver.wiretap` are enabled, logging verbose request/response payloads — fine for local debugging, should be disabled in production.
- [ ] `jjwt` is pinned to `0.11.5`, an older release with a less safe API than the current `0.12.x` series — evaluate upgrading.

## Resilience

- [ ] No explicit Resilience4j tuning (failure-rate threshold, sliding window size, wait-duration-in-open-state, etc.) is configured for any of the three circuit breakers — all rely on library defaults.
- [ ] `RedisTokenBucketRateLimiter` capacity (10) and refill rate (10 tokens/minute) are hardcoded constants, not exposed via `@ConfigurationProperties` like the rest of the app's configuration.
- [ ] No explicit gateway HTTP client connect/response timeouts are configured; the app relies entirely on Reactor Netty defaults.
- [ ] `FallbackController` returns the same generic message for every route/circuit breaker, with no per-route differentiation.

## Observability

- [ ] Correlation-ID handling is duplicated: `LoggingFilter` (GlobalFilter, order -1) and `ObservabilityWebFilter` (WebFilter, order -2) each independently generate/read `X-Correlation-ID`; only the latter populates MDC. Consolidate into a single filter.
- [ ] Logging is console-only, plain-text (not JSON-structured) — no `logback-spring.xml`, no structured encoder (e.g. logstash-logback-encoder), no file appender.
- [ ] Tracing sampling probability is set to 1.0 (always sample) — appropriate for a demo, but should be reviewed before handling real production traffic volume.
- [ ] OTLP exporter configuration is duplicated between `management.otlp.*` and the separate `otel.*` block in `application.yml` — consolidate to a single source of truth.

## Error handling

- [ ] There is no global `@ControllerAdvice` / `ErrorWebExceptionHandler`. Error response shapes are currently inconsistent: 401 (JWT filter) and 429 (rate limiter) return empty bodies, 503 (fallback controller) returns structured JSON, and any other unhandled failure falls back to Spring Boot's default error JSON shape.

## Testing

- [ ] No tests exist for `RouteConfig` (route building, predicates, path rewriting), `SecurityConfig`, `CorsConfig`, `FallbackController`, or `RedisTokenBucketRateLimiter` (the Lua-script token-bucket logic is currently untested).
- [ ] No end-to-end integration tests (`@SpringBootTest` + `WebTestClient`) exist to verify actual routing, security, and rate-limiting behavior together.
- [ ] `LoggingFilter` has no dedicated test (only `ObservabilityWebFilter` and `RateLimitFilter` are covered among the filters, plus `JwtAuthenticationFilter`).

## Deployment / Ops

- [ ] No Dockerfile, docker-compose file, Kubernetes/OpenShift manifests, or CI/CD pipeline exist in the repository, despite the project being described (in `pom.xml` and the README) as intended for OpenShift deployment. This is the largest gap between stated purpose and current state.
- [ ] `spring-boot-starter-validation` is a declared dependency but is not used anywhere in the codebase (no `@Valid`/Bean Validation annotations) — either use it or remove it.
- [ ] The reactive Redis starter (`spring-boot-starter-data-redis-reactive`) is on the classpath, but `RedisTokenBucketRateLimiter` uses the blocking `StringRedisTemplate` wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` instead of `ReactiveRedisTemplate`.
- [ ] No `application-dev.yml` / `application-prod.yml` profile separation exists — all environments currently share one `application.yml`.

## Additional verifications (not yet confirmed as bugs — need hands-on testing)

- [ ] Confirm, with real requests against a running instance, whether the Spring Security chain (`.anyExchange().authenticated()` + `httpBasic()`) actually blocks requests that carry a valid JWT but no Basic Auth header.
- [ ] Confirm gateway behavior when deployed behind a reverse proxy/load balancer with respect to `X-Forwarded-For` and client IP resolution for rate limiting.
- [ ] Load-test the default Resilience4j circuit breaker settings to understand their practical trip thresholds under realistic failure patterns.
- [ ] Verify whether the `/actuator/gateway` endpoint (exposed via `management.endpoints.web.exposure.include`) is reachable end-to-end given the current (possibly misconfigured) security chain.
