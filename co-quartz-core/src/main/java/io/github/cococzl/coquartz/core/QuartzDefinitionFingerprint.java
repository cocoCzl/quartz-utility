package io.github.cococzl.coquartz.core;

import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Date;

/**
 * Creates stable fingerprints for code-owned Quartz definitions.
 */
public final class QuartzDefinitionFingerprint {

    public static final String JOB_PROJECTION_VERSION = "1";
    public static final String SCHEDULE_PROJECTION_VERSION = "1";

    private QuartzDefinitionFingerprint() {
    }

    public static String jobFingerprint(QuartzJobDefinition definition) {
        return fingerprint(
                "job-v" + JOB_PROJECTION_VERSION,
                definition.getJobClass().getName(),
                definition.isMethodTask()
                        ? CoQuartzConstants.SOURCE_DECLARATIVE_METHOD
                        : CoQuartzConstants.SOURCE_ANNOTATED_QUARTZ_JOB,
                normalized(definition.getDescription()),
                Boolean.toString(definition.isDurable()),
                Boolean.toString(definition.isRecoverable()),
                Boolean.toString(definition.isConcurrent()),
                Integer.toString(definition.getRetryTimes()),
                Long.toString(definition.getRetryInterval()),
                Boolean.toString(definition.isExponentialBackoff()),
                Double.toString(definition.getBackoffMultiplier()),
                Long.toString(definition.getTimeout()));
    }

    public static String jobFingerprint(JobDetail jobDetail) {
        JobDataMap data = jobDetail.getJobDataMap();
        return fingerprint(
                "job-v" + JOB_PROJECTION_VERSION,
                stringValue(data, CoQuartzConstants.DELEGATE_JOB_CLASS).isBlank()
                        ? jobDetail.getJobClass().getName()
                        : stringValue(data, CoQuartzConstants.DELEGATE_JOB_CLASS),
                stringValue(data, CoQuartzConstants.TASK_SOURCE),
                normalized(jobDetail.getDescription()),
                Boolean.toString(jobDetail.isDurable()),
                Boolean.toString(jobDetail.requestsRecovery()),
                stringValue(data, CoQuartzConstants.CONCURRENT),
                stringValue(data, CoQuartzConstants.RETRY_TIMES),
                stringValue(data, CoQuartzConstants.RETRY_INTERVAL),
                stringValue(data, CoQuartzConstants.EXPONENTIAL_BACKOFF),
                stringValue(data, CoQuartzConstants.BACKOFF_MULTIPLIER),
                stringValue(data, CoQuartzConstants.TIMEOUT));
    }

    public static String scheduleFingerprint(QuartzSchedule schedule, int quartzMisfireInstruction) {
        return fingerprint(
                "schedule-v" + SCHEDULE_PROJECTION_VERSION,
                schedule.type().name(),
                normalized(schedule.cronExpression()),
                Integer.toString(schedule.intervalSeconds()),
                normalized(schedule.timeZone()),
                Integer.toString(quartzMisfireInstruction),
                "",
                "",
                Integer.toString(Trigger.DEFAULT_PRIORITY),
                schedule.type() == QuartzSchedule.Type.INTERVAL
                        ? Integer.toString(SimpleTrigger.REPEAT_INDEFINITELY)
                        : "");
    }

    public static String scheduleFingerprint(Trigger trigger) {
        if (trigger instanceof CronTrigger cronTrigger) {
            return fingerprint(
                    "schedule-v" + SCHEDULE_PROJECTION_VERSION,
                    QuartzSchedule.Type.CRON.name(),
                    normalized(cronTrigger.getCronExpression()),
                    "0",
                    normalized(cronTrigger.getTimeZone().getID()),
                    Integer.toString(cronTrigger.getMisfireInstruction()),
                    normalized(cronTrigger.getCalendarName()),
                    dateValue(cronTrigger.getEndTime()),
                    Integer.toString(cronTrigger.getPriority()),
                    "");
        }
        if (trigger instanceof SimpleTrigger simpleTrigger) {
            long intervalMs = simpleTrigger.getRepeatInterval();
            if (intervalMs % 1000 != 0 || intervalMs / 1000 > Integer.MAX_VALUE) {
                return "unsupported-simple-trigger";
            }
            return fingerprint(
                    "schedule-v" + SCHEDULE_PROJECTION_VERSION,
                    QuartzSchedule.Type.INTERVAL.name(),
                    "",
                    Long.toString(intervalMs / 1000),
                    "",
                    Integer.toString(simpleTrigger.getMisfireInstruction()),
                    normalized(simpleTrigger.getCalendarName()),
                    dateValue(simpleTrigger.getEndTime()),
                    Integer.toString(simpleTrigger.getPriority()),
                    Integer.toString(simpleTrigger.getRepeatCount()));
        }
        return "unsupported-trigger:" + trigger.getClass().getName();
    }

    public static String definitionVersion(String jobFingerprint, String scheduleFingerprint) {
        return fingerprint("definition", jobFingerprint, scheduleFingerprint);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stringValue(JobDataMap data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String dateValue(Date value) {
        return value == null ? "" : Long.toString(value.getTime());
    }

    private static String fingerprint(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) {
            String normalized = value == null ? "" : value;
            canonical.append(normalized.length()).append(':').append(normalized);
        }
        return sha256(canonical.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
