package com.company.report.knowledge.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class DataSourceCredentialCodec {
    private static final String CURRENT_PREFIX = "enc:v1:";
    private static final String LEGACY_PREFIX = "enc:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    @Autowired
    public DataSourceCredentialCodec(
            @Value("${security.data-source-credential-key:local-dev-data-source-credential-key-change-me}") String keyMaterial) {
        this(keyMaterial, new SecureRandom());
    }

    DataSourceCredentialCodec(String keyMaterial, SecureRandom secureRandom) {
        this.secretKey = new SecretKeySpec(sha256(keyMaterial), "AES");
        this.secureRandom = secureRandom;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return CURRENT_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to encrypt data source credential", ex);
        }
    }

    public String decrypt(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            return "";
        }
        if (encodedSecret.startsWith(CURRENT_PREFIX)) {
            return decryptCurrent(encodedSecret.substring(CURRENT_PREFIX.length()));
        }
        if (encodedSecret.startsWith(LEGACY_PREFIX)) {
            return new String(Base64.getDecoder().decode(encodedSecret.substring(LEGACY_PREFIX.length())), StandardCharsets.UTF_8);
        }
        return "";
    }

    public boolean isCurrent(String encodedSecret) {
        return encodedSecret != null && encodedSecret.startsWith(CURRENT_PREFIX);
    }

    private String decryptCurrent(String payload) {
        try {
            byte[] allBytes = Base64.getDecoder().decode(payload);
            ByteBuffer buffer = ByteBuffer.wrap(allBytes);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to decrypt data source credential", ex);
        }
    }

    private static byte[] sha256(String keyMaterial) {
        try {
            String normalized = keyMaterial == null || keyMaterial.isBlank()
                    ? "local-dev-data-source-credential-key-change-me"
                    : keyMaterial;
            return MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to derive data source credential key", ex);
        }
    }
}
