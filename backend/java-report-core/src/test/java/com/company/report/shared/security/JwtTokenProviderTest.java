package com.company.report.shared.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            "0123456789abcdef0123456789abcdef",
            7200
    );

    @Test
    void parsesUserStatusFromAccessToken() {
        String token = tokenProvider.generateToken(
                42L,
                List.of("analyst"),
                List.of("report:create"),
                "disabled"
        );

        CurrentUser currentUser = tokenProvider.parse(token);

        assertThat(currentUser.userId()).isEqualTo(42L);
        assertThat(currentUser.status()).isEqualTo("disabled");
        assertThat(currentUser.enabled()).isFalse();
    }

    @Test
    void parsesRs256OidcTokenFromConfiguredJwksUrl() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String kid = "oidc-key-1";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            byte[] response = jwks(kid, publicKey).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String jwksUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
            JwtTokenProvider rs256Provider = new JwtTokenProvider(
                    "0123456789abcdef0123456789abcdef",
                    7200,
                    "RS256",
                    jwksUrl,
                    "https://idp.example.com",
                    "intelligent-report-api"
            );
            String token = rs256Token(keyPair, kid, "https://idp.example.com", "intelligent-report-api");

            CurrentUser currentUser = rs256Provider.parse(token);

            assertThat(currentUser.userId()).isEqualTo(77L);
            assertThat(currentUser.roles()).containsExactly("auditor");
            assertThat(currentUser.permissions()).containsExactly("audit:read");
            assertThat(currentUser.status()).isEqualTo("enabled");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRs256ConfigurationWithoutJwksUrl() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef",
                7200,
                "RS256",
                ""
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OIDC JWKS URL");
    }

    @Test
    void rejectsRs256ConfigurationWithoutIssuer() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef",
                7200,
                "RS256",
                "http://127.0.0.1/.well-known/jwks.json",
                "",
                "intelligent-report-api"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OIDC issuer");
    }

    @Test
    void rejectsRs256ConfigurationWithoutAudience() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                "0123456789abcdef0123456789abcdef",
                7200,
                "RS256",
                "http://127.0.0.1/.well-known/jwks.json",
                "https://idp.example.com",
                ""
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OIDC audience");
    }

    @Test
    void rejectsRs256TokenWhenIssuerDoesNotMatch() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String kid = "oidc-key-issuer";
        HttpServer server = jwksServer(kid, publicKey);
        server.start();
        try {
            JwtTokenProvider rs256Provider = new JwtTokenProvider(
                    "0123456789abcdef0123456789abcdef",
                    7200,
                    "RS256",
                    jwksUrl(server),
                    "https://idp.example.com",
                    "intelligent-report-api"
            );
            String token = rs256Token(keyPair, kid, "https://unexpected-idp.example.com", "intelligent-report-api");

            assertThatThrownBy(() -> rs256Provider.parse(token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid RS256 JWT");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRs256TokenWhenAudienceDoesNotMatch() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String kid = "oidc-key-audience";
        HttpServer server = jwksServer(kid, publicKey);
        server.start();
        try {
            JwtTokenProvider rs256Provider = new JwtTokenProvider(
                    "0123456789abcdef0123456789abcdef",
                    7200,
                    "RS256",
                    jwksUrl(server),
                    "https://idp.example.com",
                    "intelligent-report-api"
            );
            String token = rs256Token(keyPair, kid, "https://idp.example.com", "other-api");

            assertThatThrownBy(() -> rs256Provider.parse(token))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid RS256 JWT");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cachesJwksForRepeatedRs256TokenVerification() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String kid = "oidc-key-cache";
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            requests.incrementAndGet();
            byte[] response = jwks(kid, publicKey).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            JwtTokenProvider rs256Provider = new JwtTokenProvider(
                    "0123456789abcdef0123456789abcdef",
                    7200,
                    "RS256",
                    jwksUrl(server),
                    "https://idp.example.com",
                    "intelligent-report-api"
            );
            String token = rs256Token(keyPair, kid, "https://idp.example.com", "intelligent-report-api");

            rs256Provider.parse(token);
            rs256Provider.parse(token);

            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refreshesCachedJwksWhenTokenKidIsRotated() throws Exception {
        KeyPair firstKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair secondKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String firstKid = "oidc-key-before-rotation";
        String secondKid = "oidc-key-after-rotation";
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> jwksResponse = new AtomicReference<>(
                jwks(firstKid, (RSAPublicKey) firstKeyPair.getPublic())
        );
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            requests.incrementAndGet();
            byte[] response = jwksResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            JwtTokenProvider rs256Provider = new JwtTokenProvider(
                    "0123456789abcdef0123456789abcdef",
                    7200,
                    "RS256",
                    jwksUrl(server),
                    "https://idp.example.com",
                    "intelligent-report-api"
            );
            String firstToken = rs256Token(firstKeyPair, firstKid, "https://idp.example.com", "intelligent-report-api");
            String secondToken = rs256Token(secondKeyPair, secondKid, "https://idp.example.com", "intelligent-report-api");

            rs256Provider.parse(firstToken);
            jwksResponse.set(jwks(secondKid, (RSAPublicKey) secondKeyPair.getPublic()));
            CurrentUser currentUser = rs256Provider.parse(secondToken);

            assertThat(currentUser.userId()).isEqualTo(77L);
            assertThat(requests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer jwksServer(String kid, RSAPublicKey publicKey) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/.well-known/jwks.json", exchange -> {
            byte[] response = jwks(kid, publicKey).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }

    private String jwksUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
    }

    private String rs256Token(KeyPair keyPair, String kid, String issuer, String audience) throws Exception {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String payload = "{"
                + "\"sub\":\"77\","
                + "\"iss\":\"" + issuer + "\","
                + "\"aud\":\"" + audience + "\","
                + "\"roles\":[\"auditor\"],"
                + "\"permissions\":[\"audit:read\"],"
                + "\"status\":\"enabled\","
                + "\"exp\":" + Instant.now().plusSeconds(300).getEpochSecond()
                + "}";
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signature.sign());
    }

    private String jwks(String kid, RSAPublicKey publicKey) {
        return "{\"keys\":[{"
                + "\"kty\":\"RSA\","
                + "\"kid\":\"" + kid + "\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"n\":\"" + base64Url(unsigned(publicKey.getModulus())) + "\","
                + "\"e\":\"" + base64Url(unsigned(publicKey.getPublicExponent())) + "\""
                + "}]}";
    }

    private byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
