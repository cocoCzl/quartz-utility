package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.quartz.CronScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.JobKey;

import java.util.Date;
import java.util.TimeZone;
import java.time.ZoneId;

/**
 * Builds Quartz triggers from Co-Quartz scheduling semantics.
 */
public final class QuartzTriggerFactory {

    private QuartzTriggerFactory() {
    }

    public static Trigger build(String triggerName,
                                String triggerGroup,
                                String cronExpression,
                                int intervalSeconds,
                                String timeZone,
                                MisfirePolicy misfirePolicy,
                                Date startAt,
                                Date endAt) {
        return build(triggerName, triggerGroup, cronExpression, intervalSeconds, timeZone,
                misfirePolicy, startAt, endAt, null);
    }

    public static Trigger build(String triggerName,
                                String triggerGroup,
                                String cronExpression,
                                int intervalSeconds,
                                String timeZone,
                                MisfirePolicy misfirePolicy,
                                Date startAt,
                                Date endAt,
                                JobKey jobKey) {
        QuartzSchedule schedule = QuartzSchedule.resolve(
                cronExpression, intervalSeconds, timeZone,
                CoQuartzConstants.DEFAULT_TIME_ZONE, misfirePolicy);
        return build(triggerName, triggerGroup, schedule, startAt, endAt, jobKey);
    }

    public static Trigger build(String triggerName,
                                String triggerGroup,
                                QuartzSchedule schedule,
                                Date startAt,
                                Date endAt,
                                JobKey jobKey) {
        if (startAt != null && endAt != null && endAt.before(startAt)) {
            throw new CoQuartzConfigurationException("endAt must not be before startAt");
        }

        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup);
        if (jobKey != null) {
            triggerBuilder.forJob(jobKey);
        }
        if (startAt != null) {
            triggerBuilder.startAt(startAt);
        } else {
            triggerBuilder.startNow();
        }
        if (endAt != null) {
            triggerBuilder.endAt(endAt);
        }

        if (schedule.type() == QuartzSchedule.Type.CRON) {
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(schedule.cronExpression())
                    .inTimeZone(TimeZone.getTimeZone(ZoneId.of(schedule.timeZone())));
            applyCronMisfirePolicy(scheduleBuilder, schedule.misfirePolicy());
            return triggerBuilder.withSchedule(scheduleBuilder).build();
        }

        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(schedule.intervalSeconds())
                .repeatForever();
        applySimpleMisfirePolicy(scheduleBuilder, schedule.misfirePolicy());
        return triggerBuilder.withSchedule(scheduleBuilder).build();
    }

    private static void applyCronMisfirePolicy(CronScheduleBuilder builder, MisfirePolicy policy) {
        if (policy == null || policy == MisfirePolicy.SMART_POLICY) {
            return;
        }
        switch (policy) {
            case FIRE_NOW -> builder.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> throw new CoQuartzConfigurationException("Unsupported cron misfire policy: " + policy);
        }
    }

    private static void applySimpleMisfirePolicy(SimpleScheduleBuilder builder, MisfirePolicy policy) {
        if (policy == null || policy == MisfirePolicy.SMART_POLICY) {
            return;
        }
        switch (policy) {
            case FIRE_NOW -> builder.withMisfireHandlingInstructionFireNow();
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> throw new CoQuartzConfigurationException("Unsupported interval misfire policy: " + policy);
        }
    }
}
