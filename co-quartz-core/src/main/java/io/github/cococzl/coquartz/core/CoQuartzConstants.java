package io.github.cococzl.coquartz.core;

public final class CoQuartzConstants {

    private CoQuartzConstants() {
    }

    public static final String PREFIX = "co-quartz.";

    public static final String RETRY_TIMES = PREFIX + "retryTimes";
    public static final String RETRY_INTERVAL = PREFIX + "retryInterval";
    public static final String EXPONENTIAL_BACKOFF = PREFIX + "exponentialBackoff";
    public static final String BACKOFF_MULTIPLIER = PREFIX + "backoffMultiplier";
    public static final String TIMEOUT = PREFIX + "timeout";
    public static final String CONCURRENT = PREFIX + "concurrent";
    public static final String MISFIRE_POLICY = PREFIX + "misfirePolicy";
    public static final String SLOW_TASK_THRESHOLD_MS = PREFIX + "slowTaskThresholdMs";

    public static final String ENHANCED = PREFIX + "enhanced";

    public static final String JOB_KEY_PREFIX = "JOB_";
    public static final String TRIGGER_KEY_PREFIX = "TRIGGER_";
    public static final String DEFAULT_GROUP = "DEFAULT";
}