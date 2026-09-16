package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Authentication is enforced by {@link com.example.gateway.filter.JwtAuthenticationFilter}
     * (a Gateway {@code GlobalFilter}), not by this chain. Spring Security's WebFilter runs
     * before Gateway routing/GlobalFilters, so requiring {@code .authenticated()} here with
     * {@code httpBasic()} would reject JWT-bearing requests before the JWT filter ever saw
     * them. This chain only disables CSRF (stateless API) and permits everything.
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
        return http.build();
    }
}
