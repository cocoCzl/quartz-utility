package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.enums.TimeEnum;
import org.quartz.*;

import java.util.Date;
import java.util.Map;

public final class SchedulerCore {

    private SchedulerCore() {
    }

    public static final String DEFAULT_GROUP = "DEFAULT";

    public static JobBuilder getJobBuilder(Class<? extends Job> jobClass, String jobName, String jobGroup,
                                            String description, boolean durable, boolean recoverable,
                                            Map<String, Object> jobDataMap) {
        JobBuilder builder = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .storeDurably(durable)
                .requestRecovery(recoverable);
        if (description != null && !description.isEmpty()) {
            builder.withDescription(description);
        }
        if (jobDataMap != null) {
            builder.usingJobData(new JobDataMap(jobDataMap));
        }
        return builder;
    }

    public static Trigger getCronTrigger(String triggerName, String triggerGroup, String cronExpression,
                                          MisfirePolicy misfirePolicy, Date startAt) {
        CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
        applyMisfirePolicy(cronBuilder, misfirePolicy);
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup)
                .withSchedule(cronBuilder)
                .startNow()
                .build();
        return trigger;
    }

    public static Trigger getSimpleTrigger(String triggerName, String triggerGroup, int intervalSeconds,
                                             MisfirePolicy misfirePolicy) {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever();
        applyMisfirePolicy(scheduleBuilder, misfirePolicy);
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup)
                .withSchedule(scheduleBuilder)
                .startNow()
                .build();
    }

    private static void applyMisfirePolicy(CronScheduleBuilder builder, MisfirePolicy misfirePolicy) {
        if (misfirePolicy == null) {
            return;
        }
        switch (misfirePolicy) {
            case FIRE_NOW -> builder.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> builder.withMisfireHandlingInstructionIgnoreMisfires();
        }
    }

    private static void applyMisfirePolicy(SimpleScheduleBuilder builder, MisfirePolicy misfirePolicy) {
        if (misfirePolicy == null) {
            return;
        }
        switch (misfirePolicy) {
            case FIRE_NOW -> builder.withMisfireHandlingInstructionFireNow();
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> builder.withMisfireHandlingInstructionNextWithExistingCount();
        }
    }

    public static JobKey getJobKey(String jobName) {
        return new JobKey(jobName, DEFAULT_GROUP);
    }

    public static JobKey getJobKey(String jobName, String jobGroup) {
        return new JobKey(jobName, jobGroup);
    }

    public static TriggerKey getTriggerKey(String triggerName) {
        return new TriggerKey(triggerName, DEFAULT_GROUP);
    }

    public static TriggerKey getTriggerKey(String triggerName, String triggerGroup) {
        return new TriggerKey(triggerName, triggerGroup);
    }
}