package com.prelude.llm;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-GCM secret cipher for BYOK provider credentials. Plaintext keys never
 * reach the database, logs, exceptions or ProblemDetails.
 */
@Component
public class ProviderSecretCipher {

    private static final int KEY_SIZE_BYTES = 32;
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_SIZE_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    public ProviderSecretCipher(@Value("${app.crypto.aes-secret}") String secret) {
        this.keySpec = buildKeySpec(secret);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_SIZE_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_SIZE_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("credential encryption failed");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= IV_SIZE_BYTES) {
                throw new IllegalArgumentException("payload too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_SIZE_BYTES];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_SIZE_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("credential decryption failed");
        }
    }

    public String mask(String encryptedText) {
        String plainText = decrypt(encryptedText);
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        int start = Math.max(plainText.length() - 4, 0);
        return "****" + plainText.substring(start);
    }

    private SecretKeySpec buildKeySpec(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("AES secret must be at least 32 bytes");
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build AES key", exception);
        }
    }
}
