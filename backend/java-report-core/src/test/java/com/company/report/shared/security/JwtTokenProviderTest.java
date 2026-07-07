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
                    jwksUrl
            );
            String token = rs256Token(keyPair, kid);

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

    private String rs256Token(KeyPair keyPair, String kid) throws Exception {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String payload = "{"
                + "\"sub\":\"77\","
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
