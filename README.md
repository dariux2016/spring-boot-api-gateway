# Spring Boot API Gateway

This project is a Spring Boot API Gateway built with Spring WebFlux and Spring Cloud Gateway. It acts as a single entry point for downstream services, centralizing routing, authentication, observability, resilience, and cross-cutting concerns for API traffic.

It is designed to be run as a containerized service and is also suitable for local development and OpenShift-style deployments.

## What this gateway does

The gateway receives incoming HTTP requests and routes them to backend services while applying a set of shared policies:

- Request routing and path rewriting
- JWT-based authentication for protected traffic
- Correlation ID propagation and structured logging
- Metrics, traces, and Prometheus export
- CORS handling for browser-based clients
- Rate limiting using a Redis-backed token bucket
- Retry and circuit breaker behavior
- Fallback responses when downstream services fail

## Architecture overview

The gateway is composed of:

- Spring Cloud Gateway route definitions for request forwarding
- Global filters for logging, authentication, observability, and rate limiting
- Configuration properties for dynamic route and behavior setup
- Spring Security for protecting selected endpoints
- Redis for distributed rate limiting state
- Micrometer + OpenTelemetry for tracing and metrics

Incoming requests flow through the filters in a predictable order, and then are forwarded to the configured upstream service.

### Architecture diagram

```text
Client
  │
  ▼
Spring Boot API Gateway
  ├─ Logging Filter
  ├─ Observability Filter
  ├─ JWT Authentication Filter
  ├─ Rate Limiting Filter
  └─ Route Router / Retry / Circuit Breaker
          │
          ├─ Products Service
          ├─ Orders Service
          └─ Secure Service

Supporting infrastructure:
- Redis for rate-limit state
- Prometheus / OpenTelemetry for metrics and traces
- Spring Security for endpoint protection
```

## Main functionalities

### 1. Dynamic routing

Routes are configured through application properties and translated into Spring Cloud Gateway routes at startup.

Configured routes include:

- /products/** -> forwards to the products service with path rewriting
- /orders/** -> forwards to the orders service with path rewriting
- /secure/** -> forwards to the secure service with path rewriting

Each route also adds a response header identifying the route and applies retry and circuit breaker policy.

### 2. Path rewriting

Each route uses a rewrite rule to transform the inbound path into the downstream service path.

Example:

- Incoming: /products/123
- Rewritten: /products-api/123

This is controlled by the route configuration in the application properties.

### 3. Authentication with JWT

The gateway includes a JWT authentication filter that:

- Checks for an Authorization header with a Bearer token
- Validates the token using a signing key
- Extracts claims such as subject, name, email, and roles
- Injects those claims as request headers for downstream services

If the token is missing or invalid, the gateway returns HTTP 401 Unauthorized.

> The current implementation uses a demo secret and should be replaced with a production-grade secret management strategy.

### 4. Security

Authentication is enforced entirely by the JWT authentication filter (see above), not by Spring Security's reactive filter chain.

Spring Security's `SecurityWebFilterChain` runs as a `WebFilter`, which executes *before* Spring Cloud Gateway's routing and global filters — so a chain requiring `.anyExchange().authenticated()` there would reject requests before the JWT filter ever saw them. To avoid that ordering conflict, the Spring Security chain in this gateway only disables CSRF (stateless API) and permits all exchanges; it does not perform authentication itself.

> Role-based authorization at the gateway level (beyond forwarding roles via `X-User-Roles` for downstream services to interpret) is not yet implemented. See [TODO.md](TODO.md) for details.

### 5. Observability and tracing

The gateway adds correlation IDs to requests and stores them in MDC (Mapped Diagnostic Context) so logs are easier to correlate across services.

It also supports:

- Correlation ID headers
- Trace/span context propagation
- Structured console logging with correlation and trace identifiers
- Metrics export to Prometheus
- Distributed tracing via OpenTelemetry to an OTLP collector

### 6. Resilience patterns

The gateway is designed to be resilient in the face of downstream failures.

It includes:

- Retry policy for transient failures
- Circuit breaker configuration per route
- Fallback endpoint for service unavailability

If a downstream dependency fails repeatedly, the gateway can route the request to the fallback endpoint instead of failing immediately.

### 7. Rate limiting

The gateway implements a token bucket rate limiter backed by Redis.

Behavior:

- Each client IP is mapped to a rate-limiting bucket
- Requests consume tokens from the bucket
- If the bucket is empty, the gateway responds with HTTP 429 Too Many Requests

This provides a simple but effective protection layer against request bursts.

### 8. CORS support

The gateway includes CORS configuration for browser clients.

Allowed origins, methods, headers, and exposed headers are configurable, and credentials are enabled by default for the configured local development origins.

## Configuration highlights

The gateway is configured through the application properties file and uses a strongly typed configuration model.

Key configuration areas include:

- Server port
- Gateway routes and rewrite rules
- Observability headers
- CORS policy
- Redis connection details
- Actuator exposure
- Tracing and metrics exporters

### Example configuration structure

The application properties define three route groups and their related resilience settings, including:

- Retry counts and backoff durations
- Circuit breaker names and fallback targets
- Response headers for route identification

## Running the application locally

### Prerequisites

- Java 21
- Maven
- Redis running locally on port 6379
- Optional: an OpenTelemetry collector or Jaeger/Tempo-compatible endpoint for traces

### Start the gateway

```bash
mvn spring-boot:run
```

The service will start on port 8080 by default.

### Example requests

#### Unauthenticated request to a protected route

```bash
curl -i http://localhost:8080/products/123
```

This will likely return 401 unless the request includes a valid Bearer token.

#### Request with a JWT

```bash
curl -i http://localhost:8080/products/123 \
  -H "Authorization: Bearer <your-token>"
```

#### Health check

```bash
curl http://localhost:8080/actuator/health
```

#### Prometheus metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

## API and endpoint behavior

### Public endpoints

The gateway allows unauthenticated access to:

- /actuator/health
- /actuator/info
- /actuator/prometheus
- /fallback

### Protected endpoints

All other routes are protected by the security configuration and require authentication.

## Fallback behavior

If a route’s downstream service is unavailable, the gateway returns a JSON response from the fallback controller:

```json
{
  "status": "error",
  "message": "Service temporarily unavailable. Please try again later."
}
```

## Testing

The project includes unit tests for the gateway filters, covering:

- JWT validation success and failure
- Correlation ID injection and MDC propagation
- Rate limiting decisions

Run the test suite with:

```bash
mvn test
```

## Notes for production use

For a production deployment, consider:

- Replacing the JWT secret configuration with a secure secret store (startup already fails fast if `JWT_SECRET` is missing or empty)
- Adding real per-route role-based authorization at the gateway (roles from the JWT are currently only forwarded as `X-User-Roles` for downstream services to interpret) — see [TODO.md](TODO.md)
- Configuring real upstream service URLs rather than placeholder hosts, ideally via service discovery or a load-balanced (`lb://`) URI scheme
- Enabling proper authentication and authorization for downstream services
- Securing Redis credentials and network access
- Adjusting rate limits and circuit breaker settings to match real traffic patterns, and tuning Resilience4j explicitly (failure-rate thresholds, sliding window, wait-duration-in-open-state) instead of relying on defaults
- Setting explicit HTTP client connect/response timeouts at the gateway level
- Using a managed observability stack for logs, metrics, and traces, and removing the duplicated correlation-ID handling between the logging and observability filters
- Providing deployment artifacts (Dockerfile, OpenShift/Kubernetes manifests, CI/CD pipeline) — none are currently included in this repository despite the project's stated OpenShift deployment target

See [TODO.md](TODO.md) for the full list of known limitations, improvements, and open verifications.

## Known limitations

This project is a functional reference implementation, but the following gaps should be understood before treating it as production-ready:

- No deployment artifacts (Dockerfile, Kubernetes/OpenShift manifests, CI/CD pipeline) despite the project being described as intended for OpenShift-style deployment
- Test coverage is limited to isolated unit tests for three filters; there is no end-to-end integration test exercising real routing, security, or rate-limiting behavior
- Rate limiting keys on the raw remote IP and does not account for `X-Forwarded-For`, so all traffic behind a reverse proxy or load balancer would share one bucket
- Routes point to placeholder hostnames with no service discovery or load-balancer (`lb://`) integration

Full details, severity, and additional items are tracked in [TODO.md](TODO.md).

## Summary

This project is a practical example of a modern API gateway that combines routing, security, resilience, observability, and traffic control in one Spring Boot service. It is well-suited as a learning project, a reference architecture, or a starting point for a more feature-rich gateway deployment.

