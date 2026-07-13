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
    public static final String TIME_ZONE = PREFIX + "timeZone";
    public static final String SLOW_TASK_THRESHOLD_MS = PREFIX + "slowTaskThresholdMs";

    public static final String ENHANCED = PREFIX + "enhanced";
    public static final String METHOD_TASK = PREFIX + "methodTask";
    public static final String OWNER = PREFIX + "owner";
    public static final String TASK_SOURCE = PREFIX + "taskSource";
    public static final String CODE_OWNED = PREFIX + "codeOwned";
    public static final String METADATA_VERSION = PREFIX + "metadataVersion";
    public static final String JOB_FINGERPRINT = PREFIX + "jobFingerprint";
    public static final String SCHEDULE_FINGERPRINT = PREFIX + "scheduleFingerprint";
    public static final String DEFINITION_VERSION = PREFIX + "definitionVersion";
    public static final String PAUSE_RESTORE_PENDING = PREFIX + "pauseRestorePending";
    /** Original application Job class when Quartz schedules the non-concurrent proxy. */
    public static final String DELEGATE_JOB_CLASS = PREFIX + "delegateJobClass";
    public static final String RETRY_EXECUTION_ID = PREFIX + "retryExecutionId";
    public static final String RETRY_ATTEMPT = PREFIX + "retryAttempt";
    public static final String RETRY_TRIGGER_GROUP = "CO_QUARTZ_RETRY";
    public static final String RELIABLE_AUDIT_CONTEXT = PREFIX + "reliableAuditLog";

    public static final String OWNER_VALUE = "co-quartz";
    public static final String SOURCE_DECLARATIVE_METHOD = "DECLARATIVE_METHOD";
    public static final String SOURCE_ANNOTATED_QUARTZ_JOB = "ANNOTATED_QUARTZ_JOB";
    public static final String SOURCE_DYNAMIC = "DYNAMIC";
    public static final String METADATA_VERSION_VALUE = "1";

    public static final String JOB_KEY_PREFIX = "JOB_";
    public static final String TRIGGER_KEY_PREFIX = "TRIGGER_";
    public static final String DEFAULT_GROUP = "DEFAULT";
    public static final String DEFAULT_TIME_ZONE = "UTC";
}
