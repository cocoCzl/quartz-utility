package io.github.cococzl.coquartz.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLogSanitizerTest {

    private final DefaultLogSanitizer sanitizer = new DefaultLogSanitizer();

    @Test
    void masksCommonCredentialKeyValuePatterns() {
        String sanitized = sanitizer.sanitize("password=hunter2 token: abc secret=xyz authorization Bearer-123");

        assertThat(sanitized).isEqualTo("password=*** token: *** secret=*** authorization ***");
    }

    @Test
    void leavesOrdinaryDiagnosticTextUnchanged() {
        assertThat(sanitizer.sanitize("Connection refused while invoking inventory service"))
                .isEqualTo("Connection refused while invoking inventory service");
    }
}
