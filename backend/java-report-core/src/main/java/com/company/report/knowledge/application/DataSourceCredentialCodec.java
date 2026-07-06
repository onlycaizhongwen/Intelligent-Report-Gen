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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DataSourceCredentialCodec {
    private static final String CURRENT_PREFIX = "enc:v1:";
    private static final String KEYED_PREFIX = "enc:v2:";
    private static final String LEGACY_PREFIX = "enc:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec currentSecretKey;
    private final Map<String, SecretKeySpec> keyring;
    private final SecureRandom secureRandom;
    private final String currentPrefix;

    @Autowired
    public DataSourceCredentialCodec(
            @Value("${security.data-source-credential-key:local-dev-data-source-credential-key-change-me}") String keyMaterial,
            @Value("${security.data-source-credential-key-id:local-dev}") String keyId,
            @Value("${security.data-source-credential-previous-keys:}") String previousKeys) {
        this(keyId, keyMaterial, parsePreviousKeys(previousKeys), new SecureRandom());
    }

    public DataSourceCredentialCodec(String keyMaterial) {
        this(keyMaterial, new SecureRandom());
    }

    DataSourceCredentialCodec(String keyMaterial, SecureRandom secureRandom) {
        this(keyMaterial, secureRandom, CURRENT_PREFIX);
    }

    DataSourceCredentialCodec(String keyId, String keyMaterial, SecureRandom secureRandom) {
        this(keyId, keyMaterial, Map.of(), secureRandom);
    }

    DataSourceCredentialCodec(
            String keyId,
            String keyMaterial,
            Map<String, String> previousKeyMaterials,
            SecureRandom secureRandom) {
        this(keyMaterial, secureRandom, KEYED_PREFIX + normalizeKeyId(keyId) + ":");
        previousKeyMaterials.forEach((previousKeyId, previousKeyMaterial) ->
                keyring.put(normalizeKeyId(previousKeyId), new SecretKeySpec(sha256(previousKeyMaterial), "AES")));
    }

    private DataSourceCredentialCodec(String keyMaterial, SecureRandom secureRandom, String currentPrefix) {
        this.currentSecretKey = new SecretKeySpec(sha256(keyMaterial), "AES");
        this.keyring = new LinkedHashMap<>();
        this.secureRandom = secureRandom;
        this.currentPrefix = currentPrefix;
        currentKeyId(currentPrefix).ifPresent(keyId -> keyring.put(keyId, currentSecretKey));
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, currentSecretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return currentPrefix + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to encrypt data source credential", ex);
        }
    }

    public String decrypt(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            return "";
        }
        if (encodedSecret.startsWith(CURRENT_PREFIX)) {
            return decryptCurrent(encodedSecret.substring(CURRENT_PREFIX.length()), currentSecretKey);
        }
        if (encodedSecret.startsWith(KEYED_PREFIX)) {
            int payloadStart = encodedSecret.indexOf(':', KEYED_PREFIX.length());
            if (payloadStart > KEYED_PREFIX.length()) {
                String keyId = encodedSecret.substring(KEYED_PREFIX.length(), payloadStart);
                SecretKeySpec secretKey = keyring.get(keyId);
                if (secretKey == null) {
                    throw new IllegalStateException("unknown data source credential key id: " + keyId);
                }
                return decryptCurrent(encodedSecret.substring(payloadStart + 1), secretKey);
            }
            return "";
        }
        if (encodedSecret.startsWith(LEGACY_PREFIX)) {
            return new String(Base64.getDecoder().decode(encodedSecret.substring(LEGACY_PREFIX.length())), StandardCharsets.UTF_8);
        }
        return "";
    }

    public boolean isCurrent(String encodedSecret) {
        return encodedSecret != null && encodedSecret.startsWith(currentPrefix);
    }

    private String decryptCurrent(String payload, SecretKeySpec secretKey) {
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

    private static String normalizeKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return "default";
        }
        return keyId.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static Map<String, String> parsePreviousKeys(String previousKeys) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (previousKeys == null || previousKeys.isBlank()) {
            return parsed;
        }
        for (String entry : previousKeys.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            parsed.put(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
        }
        return parsed;
    }

    private static java.util.Optional<String> currentKeyId(String prefix) {
        if (!prefix.startsWith(KEYED_PREFIX)) {
            return java.util.Optional.empty();
        }
        int payloadStart = prefix.indexOf(':', KEYED_PREFIX.length());
        if (payloadStart <= KEYED_PREFIX.length()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(prefix.substring(KEYED_PREFIX.length(), payloadStart));
    }
}
