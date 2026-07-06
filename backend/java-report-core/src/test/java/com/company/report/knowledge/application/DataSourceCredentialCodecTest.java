package com.company.report.knowledge.application;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceCredentialCodecTest {

    @Test
    void encryptsCredentialsWithCurrentKeyIdForRotationChecks() {
        DataSourceCredentialCodec codec = new DataSourceCredentialCodec(
                "primary-2026-07",
                "current-data-source-key",
                deterministicRandom());

        String encrypted = codec.encrypt("plain-secret");

        assertThat(encrypted).startsWith("enc:v2:primary-2026-07:");
        assertThat(encrypted).doesNotContain("plain-secret");
        assertThat(codec.decrypt(encrypted)).isEqualTo("plain-secret");
        assertThat(codec.isCurrent(encrypted)).isTrue();
        assertThat(codec.isCurrent(encrypted.replace("primary-2026-07", "retired-2026-06"))).isFalse();
    }

    private SecureRandom deterministicRandom() {
        return new SecureRandom(new byte[]{1, 2, 3, 4});
    }
}
