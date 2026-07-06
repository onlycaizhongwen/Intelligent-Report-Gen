package com.company.report.shared.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
