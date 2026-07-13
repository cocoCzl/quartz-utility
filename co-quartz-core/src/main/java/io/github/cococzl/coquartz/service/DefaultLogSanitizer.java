package io.github.cococzl.coquartz.service;

import java.util.regex.Pattern;

/** Default conservative redaction for common credentials in exception messages and stack traces. */
public class DefaultLogSanitizer implements LogSanitizer {
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)\\b(password|secret|token|authorization)\\b(\\s*[:=]\\s*|\\s+)([^\\s,;}&]+)");

    @Override
    public String sanitize(String value) {
        if (value == null) return null;
        return SENSITIVE_VALUE.matcher(value).replaceAll("$1$2***");
    }
}
