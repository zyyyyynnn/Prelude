package com.prelude.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BYOK credentials are only worth encrypting if the key came from the deployment, so an
 * unconfigured or trivially short key must stop startup instead of silently proceeding.
 */
class ProviderSecretCipherTest {

    private static final String OPERATOR_SECRET = "a-unique-operator-selected-secret-value-32b";

    @Test
    void refusesAnUnconfiguredSecret() {
        assertThatThrownBy(() -> new ProviderSecretCipher(""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not configured");

        assertThatThrownBy(() -> new ProviderSecretCipher(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not configured");
    }

    @Test
    void refusesATooShortSecret() {
        assertThatThrownBy(() -> new ProviderSecretCipher("too-short"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void roundTripsAndMasksWithoutExposingThePlaintext() {
        ProviderSecretCipher cipher = new ProviderSecretCipher(OPERATOR_SECRET);

        String encrypted = cipher.encrypt("sk-secret-provider-key");

        assertThat(encrypted).doesNotContain("sk-secret-provider-key");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-secret-provider-key");
        assertThat(cipher.mask(encrypted)).isEqualTo("****-key");
    }
}
