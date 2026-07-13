package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
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
        return getCronTrigger(triggerName, triggerGroup, cronExpression,
                CoQuartzConstants.DEFAULT_TIME_ZONE, misfirePolicy, startAt);
    }

    public static Trigger getCronTrigger(String triggerName, String triggerGroup, String cronExpression,
                                          String timeZone, MisfirePolicy misfirePolicy, Date startAt) {
        return QuartzTriggerFactory.build(triggerName, triggerGroup, cronExpression, 0,
                timeZone, misfirePolicy, startAt, null);
    }

    public static Trigger getSimpleTrigger(String triggerName, String triggerGroup, int intervalSeconds,
                                             MisfirePolicy misfirePolicy) {
        return QuartzTriggerFactory.build(triggerName, triggerGroup, "", intervalSeconds,
                CoQuartzConstants.DEFAULT_TIME_ZONE, misfirePolicy, null, null);
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
