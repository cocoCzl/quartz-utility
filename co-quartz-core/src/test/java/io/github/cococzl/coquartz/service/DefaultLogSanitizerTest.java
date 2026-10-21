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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Authorization: Bearer secret-value", "AUTHORIZATION: Basic secret-value",
            "{\"password\":\"secret-value\"}", "{\"nested\":{\"access_token\":\"secret-value\"}}",
            "https://service/path?api_key=secret-value&safe=yes",
            "jdbc:mysql://user:secret-value@db/app", "jdbc:postgresql://db/app?password=secret-value",
            "Caused by: java.lang.Exception: {\"PASSWORD\": \"secret-value\"}"
    })
    void masksCredentialsInCommonTransportAndStructuredFormats(String input) {
        assertThat(sanitizer.sanitize(input)).doesNotContain("secret-value");
    }

    @Test
    void leavesOrdinaryDiagnosticTextUnchanged() {
        assertThat(sanitizer.sanitize("Connection refused while invoking inventory service"))
                .isEqualTo("Connection refused while invoking inventory service");
    }
}
