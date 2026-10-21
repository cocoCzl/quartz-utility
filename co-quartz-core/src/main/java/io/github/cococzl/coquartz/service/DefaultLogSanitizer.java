package io.github.cococzl.coquartz.service;

import java.util.regex.Pattern;

/** Best-effort redaction of common credential formats, not a general secret detector. */
public class DefaultLogSanitizer implements LogSanitizer {
    private static final String KEYS = "password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|api[_-]?key|authorization";
    private static final Pattern AUTH = Pattern.compile(
            "(?i)(\\bauthorization[\"']?\\s*[:=]\\s*[\"']?)(?:Bearer|Basic)\\s+[^\\s\"',;}]+" );
    private static final Pattern QUOTED = Pattern.compile(
            "(?i)([\"'](?:" + KEYS + ")[\"']\\s*[:=]\\s*)(\"(?:[^\"\\\\]++|\\\\.)*+\"|'(?:[^'\\\\]++|\\\\.)*+')");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(" + KEYS + ")\\b(\\s*[:=]\\s*|\\s+)([^\\s,;}&\"']+)" );
    private static final Pattern URI = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://)[^\\s/@:]{1,256}:[^\\s/@]{1,4096}@");

    @Override
    public String sanitize(String value) {
        if (value == null) return null;
        String result = AUTH.matcher(value).replaceAll("$1***");
        result = QUOTED.matcher(result).replaceAll("$1\"***\"");
        result = KEY_VALUE.matcher(result).replaceAll("$1$2***");
        return URI.matcher(result).replaceAll("$1***:***@");
    }
}
