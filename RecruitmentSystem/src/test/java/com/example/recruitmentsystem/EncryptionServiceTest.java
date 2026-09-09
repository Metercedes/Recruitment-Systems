package com.example.recruitmentsystem;

import com.example.recruitmentsystem.service.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-07: the encryption key was generated at startup and printed to standard output. */
class EncryptionServiceTest {

    private static final String KEY = "cmVjcnVpdG1lbnQtcGxhdGZvcm0tdGVzdC1rZXktMzI=";

    @Test
    @DisplayName("construction fails when no key is configured, rather than inventing one")
    void missingKeyIsFatal() {
        assertThatThrownBy(() -> new EncryptionService(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("a key of the wrong length is rejected")
    void wrongLengthIsRejected() {
        String shortKey = java.util.Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> new EncryptionService(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("a key that is not Base64 is rejected")
    void nonBase64IsRejected() {
        assertThatThrownBy(() -> new EncryptionService("!!! not base64 !!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ciphertext round-trips")
    void roundTrip() {
        EncryptionService service = new EncryptionService(KEY);
        assertThat(service.decrypt(service.encrypt("sensitive"))).isEqualTo("sensitive");
    }

    @Test
    @DisplayName("the same plaintext encrypts differently each time, because the IV is random")
    void ivIsNotReused() {
        EncryptionService service = new EncryptionService(KEY);
        assertThat(service.encrypt("same")).isNotEqualTo(service.encrypt("same"));
    }

    @Test
    @DisplayName("two instances with the same configured key can read each other's ciphertext")
    void keyIsStableAcrossInstances() {
        String ciphertext = new EncryptionService(KEY).encrypt("survives a restart");
        assertThat(new EncryptionService(KEY).decrypt(ciphertext)).isEqualTo("survives a restart");
    }
}
