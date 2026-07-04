package com.twin.auth.security;

import io.jsonwebtoken.io.Decoders;
import com.twin.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-min:15}") long accessTtlMin,
            @Value("${app.jwt.refresh-ttl-days:7}") long refreshTtlDays) {

        byte[] keyBytes = Decoders.BASE64.decode(secret);

        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "JWT secret too weak for HS512. decoded bytes=" + keyBytes.length
            );
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlMs = Duration.ofMinutes(accessTtlMin).toMillis();
        this.refreshTtlMs = Duration.ofDays(refreshTtlDays).toMillis();
    }

    public String generateAccess(User u, Set<String> roles, Set<String> perms) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(u.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("uid", u.getId())
                .claim("roles", roles)
                .claim("perms", perms)
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTtlMs)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefresh(User u) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(u.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("uid", u.getId())
                .claim("typ", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(refreshTtlMs)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /** 驗簽 + 解析；簽章/過期錯誤會丟 JwtException。gateway 第 ② 階段會用同一把 key 驗。 */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
