package com.company.report.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JwtTokenProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;
    private final String algorithm;
    private final String oidcJwksUrl;
    private final String oidcIssuer;
    private final String oidcAudience;

    @Autowired
    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds,
            @Value("${security.jwt.algorithm:HS256}") String algorithm,
            @Value("${security.jwt.oidc-jwks-url:}") String oidcJwksUrl,
            @Value("${security.jwt.oidc-issuer:}") String oidcIssuer,
            @Value("${security.jwt.oidc-audience:}") String oidcAudience
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.algorithm = normalizeAlgorithm(algorithm);
        this.oidcJwksUrl = oidcJwksUrl == null ? "" : oidcJwksUrl.trim();
        this.oidcIssuer = oidcIssuer == null ? "" : oidcIssuer.trim();
        this.oidcAudience = oidcAudience == null ? "" : oidcAudience.trim();
        if ("RS256".equals(this.algorithm)) {
            if (this.oidcJwksUrl.isBlank()) {
                throw new IllegalArgumentException("OIDC JWKS URL is required when security.jwt.algorithm=RS256");
            }
            if (this.oidcIssuer.isBlank()) {
                throw new IllegalArgumentException("OIDC issuer is required when security.jwt.algorithm=RS256");
            }
            if (this.oidcAudience.isBlank()) {
                throw new IllegalArgumentException("OIDC audience is required when security.jwt.algorithm=RS256");
            }
        }
    }

    public JwtTokenProvider(String secret, long accessTokenTtlSeconds) {
        this(secret, accessTokenTtlSeconds, "HS256", "", "", "");
    }

    public JwtTokenProvider(String secret, long accessTokenTtlSeconds, String algorithm, String oidcJwksUrl) {
        this(secret, accessTokenTtlSeconds, algorithm, oidcJwksUrl, "", "");
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
        if ("RS256".equals(algorithm)) {
            return parseRs256(token);
        }
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        Long userId = Long.parseLong(claims.getSubject());
        Set<String> roles = Set.copyOf((List<String>) claims.getOrDefault("roles", List.of()));
        Set<String> permissions = Set.copyOf((List<String>) claims.getOrDefault("permissions", List.of()));
        String status = String.valueOf(claims.getOrDefault("status", "enabled"));
        return new CurrentUser(userId, roles, permissions, status);
    }

    private CurrentUser parseRs256(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid JWT format");
            }
            Map<String, Object> header = decodeJson(parts[0]);
            if (!"RS256".equals(String.valueOf(header.get("alg")))) {
                throw new IllegalArgumentException("unsupported JWT alg");
            }
            String kid = String.valueOf(header.getOrDefault("kid", ""));
            PublicKey publicKey = resolveJwksPublicKey(kid);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(base64UrlDecode(parts[2]))) {
                throw new IllegalArgumentException("invalid JWT signature");
            }
            Map<String, Object> payload = decodeJson(parts[1]);
            Object exp = payload.get("exp");
            if (exp instanceof Number number && number.longValue() < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("JWT expired");
            }
            validateIssuer(payload);
            validateAudience(payload);
            Long userId = Long.parseLong(String.valueOf(payload.get("sub")));
            Set<String> roles = Set.copyOf(stringList(payload.get("roles")));
            Set<String> permissions = Set.copyOf(stringList(payload.get("permissions")));
            String status = String.valueOf(payload.getOrDefault("status", "enabled"));
            return new CurrentUser(userId, roles, permissions, status);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid RS256 JWT", ex);
        }
    }

    private void validateIssuer(Map<String, Object> payload) {
        String tokenIssuer = String.valueOf(payload.getOrDefault("iss", ""));
        if (!oidcIssuer.equals(tokenIssuer)) {
            throw new IllegalArgumentException("JWT issuer mismatch");
        }
    }

    private void validateAudience(Map<String, Object> payload) {
        Object audience = payload.get("aud");
        if (audience instanceof List<?> list) {
            boolean matched = list.stream().map(String::valueOf).anyMatch(oidcAudience::equals);
            if (matched) {
                return;
            }
        } else if (oidcAudience.equals(String.valueOf(audience))) {
            return;
        }
        throw new IllegalArgumentException("JWT audience mismatch");
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private PublicKey resolveJwksPublicKey(String kid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(oidcJwksUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        String body = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .body();
        Map<String, Object> jwks = JSON.readValue(body, JSON_MAP);
        Object keysValue = jwks.get("keys");
        if (!(keysValue instanceof List<?> keys)) {
            throw new IllegalArgumentException("JWKS keys missing");
        }
        for (Object keyValue : keys) {
            if (!(keyValue instanceof Map<?, ?> key)) {
                continue;
            }
            Object kidValue = key.get("kid");
            String keyKid = kidValue == null ? "" : String.valueOf(kidValue);
            if (!kid.isBlank() && !kid.equals(keyKid)) {
                continue;
            }
            if (!"RSA".equals(String.valueOf(key.get("kty")))) {
                continue;
            }
            BigInteger modulus = new BigInteger(1, base64UrlDecode(String.valueOf(key.get("n"))));
            BigInteger exponent = new BigInteger(1, base64UrlDecode(String.valueOf(key.get("e"))));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        }
        throw new IllegalArgumentException("matching JWKS RSA key not found");
    }

    private Map<String, Object> decodeJson(String base64Url) throws Exception {
        return JSON.readValue(base64UrlDecode(base64Url), JSON_MAP);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String normalizeAlgorithm(String value) {
        String normalized = value == null || value.isBlank() ? "HS256" : value.trim().toUpperCase();
        if (!Set.of("HS256", "RS256").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported JWT algorithm: " + value);
        }
        return normalized;
    }
}
