package com.interview.security;

import com.interview.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptorTest {

    @Test
    void encryptsAndDecryptsApiKeyWithoutReturningPlaintext() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor("0123456789abcdef0123456789abcdef");

        String encrypted = encryptor.encrypt("sk-test-123456");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).doesNotContain("sk-test-123456");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("sk-test-123456");
        assertThat(encryptor.mask(encrypted)).isEqualTo("****3456");
    }

    @Test
    void rejectsShortSecrets() {
        assertThatThrownBy(() -> new AesGcmEncryptor("short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void wrapsDecryptFailuresAsBusinessErrors() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor("0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> encryptor.decrypt("not-valid-base64"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("API Key 解密失败");
    }
}
