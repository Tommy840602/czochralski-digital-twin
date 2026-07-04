package com.twin.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PREFIXES =
            List.of("/auth/", "/actuator/", "/oauth2/", "/login/","/ws/");

    private final SecretKey key;

    public JwtAuthGlobalFilter(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);

        log.debug("JWT secret decoded bytes = {}", keyBytes.length);

        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "JWT secret too weak for HS512. decoded bytes=" + keyBytes.length
            );
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getPath().value();

        ServerHttpRequest cleaned = req.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Name");
                    h.remove("X-User-Roles");
                    h.remove("X-User-Perms");
                })
                .build();

        if (req.getMethod() == HttpMethod.OPTIONS || isPublic(path) || isWebSocketUpgrade(cleaned)) {
            return chain.filter(exchange.mutate().request(cleaned).build());
        }

        String auth = cleaned.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少 Bearer token");
        }

        Claims c;
        try {
            c = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(auth.substring(7))
                    .getPayload();
        } catch (Exception e) {
            return unauthorized(exchange, "token 無效: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        if (!"access".equals(c.get("typ", String.class))) {
            return unauthorized(exchange, "需要 access token");
        }

        Object uid = c.get("uid");

        ServerHttpRequest mutated = cleaned.mutate()
                .header("X-User-Id", uid == null ? "" : uid.toString())
                .header("X-User-Name", c.getSubject() == null ? "" : c.getSubject())
                .header("X-User-Roles", joinClaim(c, "roles"))
                .header("X-User-Perms", joinClaim(c, "perms"))
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isWebSocketUpgrade(ServerHttpRequest req) {
        String upgrade = req.getHeaders().getFirst("Upgrade");
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade);
    }

    private String joinClaim(Claims c, String name) {
        Object v = c.get(name);
        if (v instanceof Collection<?> col) {
            return col.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }
        return v == null ? "" : v.toString();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.UNAUTHORIZED);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String safeMsg = msg == null ? "" : msg
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        byte[] body = ("{\"status\":401,\"message\":\"" + safeMsg + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        return resp.writeWith(Mono.just(resp.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
