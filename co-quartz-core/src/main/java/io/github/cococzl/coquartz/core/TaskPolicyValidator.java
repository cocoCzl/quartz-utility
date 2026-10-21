package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;

/** Shared task-level limits bound retry work and avoid arithmetic overflow. Delays are milliseconds. */
public final class TaskPolicyValidator {
    private TaskPolicyValidator() { }
    public static void validate(int retries, long interval, double multiplier, long timeout, String source) {
        require(retries >= 0 && retries <= 1000, source, "retryTimes must be 0..1000");
        require(interval >= 0 && interval <= 60000, source, "retryInterval must be 0..60000ms");
        require(Double.isFinite(multiplier) && multiplier >= 1 && multiplier <= 100, source,
                "backoffMultiplier must be finite and 1..100");
        require(timeout >= 0 && timeout <= 604800000L, source, "timeout must be 0..604800000ms");
    }
    private static void require(boolean valid, String source, String message) {
        if (!valid) throw new CoQuartzConfigurationException(source + ": " + message);
    }
}
