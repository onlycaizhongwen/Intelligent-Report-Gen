package com.company.report.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Component
public class JwtTokenProvider {
    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / JWT Access Token 两小时有效 */
    public String generateToken(Long userId, List<String> roles, List<String> permissions) {
        return generateToken(userId, roles, permissions, "enabled");
    }

    /** OpenSpec: permission-collaboration / REQ-AUTH-001 / Access Token carries account status for disabled-account enforcement. */
    public String generateToken(Long userId, List<String> roles, List<String> permissions, String status) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("status", status == null || status.isBlank() ? "enabled" : status)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .signWith(secretKey)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        Long userId = Long.parseLong(claims.getSubject());
        Set<String> roles = Set.copyOf((List<String>) claims.getOrDefault("roles", List.of()));
        Set<String> permissions = Set.copyOf((List<String>) claims.getOrDefault("permissions", List.of()));
        String status = String.valueOf(claims.getOrDefault("status", "enabled"));
        return new CurrentUser(userId, roles, permissions, status);
    }
}
