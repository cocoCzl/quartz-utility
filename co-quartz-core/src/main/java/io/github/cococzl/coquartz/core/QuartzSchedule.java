package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.quartz.CronExpression;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Validated, normalized scheduling definition used by trigger creation and reconciliation.
 */
public record QuartzSchedule(Type type,
                             String cronExpression,
                             int intervalSeconds,
                             String timeZone,
                             MisfirePolicy misfirePolicy) {

    public enum Type {
        CRON,
        INTERVAL
    }

    public static QuartzSchedule resolve(String cronExpression,
                                         int intervalSeconds,
                                         String declaredTimeZone,
                                         String defaultTimeZone,
                                         MisfirePolicy misfirePolicy) {
        if (intervalSeconds < 0) {
            throw new CoQuartzConfigurationException("intervalSeconds must not be negative");
        }
        boolean hasCron = cronExpression != null && !cronExpression.isBlank();
        boolean hasInterval = intervalSeconds > 0;
        if (hasCron == hasInterval) {
            throw new CoQuartzConfigurationException(
                    "Exactly one of cron or intervalSeconds must be configured");
        }
        if (misfirePolicy == null) {
            throw new CoQuartzConfigurationException("misfirePolicy must not be null");
        }

        if (hasInterval) {
            return new QuartzSchedule(Type.INTERVAL, null, intervalSeconds, null, misfirePolicy);
        }

        String cron = cronExpression.trim();
        if (!CronExpression.isValidExpression(cron)) {
            throw new CoQuartzConfigurationException("Invalid cron expression: " + cronExpression);
        }
        String zone = declaredTimeZone == null || declaredTimeZone.isBlank()
                ? defaultTimeZone
                : declaredTimeZone;
        try {
            zone = TimeZone.getTimeZone(ZoneId.of(zone)).getID();
        } catch (DateTimeException | NullPointerException e) {
            throw new CoQuartzConfigurationException("Invalid scheduling time zone: " + zone, e);
        }
        return new QuartzSchedule(Type.CRON, cron, 0, zone, misfirePolicy);
    }
}
