package io.github.cococzl.coquartz.service;

/** Sanitizes diagnostic text before it enters Co-Quartz logs, storage, or events. */
@FunctionalInterface
public interface LogSanitizer {
    String sanitize(String value);
}
