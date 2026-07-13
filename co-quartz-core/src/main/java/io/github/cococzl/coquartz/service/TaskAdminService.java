package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.core.QuartzTriggerFactory;
import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CodeOwnedTaskModificationException;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TaskAdminService {

    private final Scheduler scheduler;
    private final String defaultTimeZone;

    public TaskAdminService(Scheduler scheduler) {
        this(scheduler, CoQuartzConstants.DEFAULT_TIME_ZONE);
    }

    public TaskAdminService(Scheduler scheduler, CoQuartzProperties properties) {
        this(scheduler, properties.getScheduling().getDefaultTimeZone());
    }

    private TaskAdminService(Scheduler scheduler, String defaultTimeZone) {
        this.scheduler = scheduler;
        this.defaultTimeZone = defaultTimeZone;
    }

    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey(jobName, jobGroup));
    }

    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey(jobName, jobGroup));
    }

    public boolean deleteJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        assertDefinitionIsMutable(jobKey);
        return scheduler.deleteJob(jobKey);
    }

    public void triggerNow(String jobName, String jobGroup) throws SchedulerException {
        scheduler.triggerJob(JobKey.jobKey(jobName, jobGroup));
    }

    public boolean exists(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.checkExists(JobKey.jobKey(jobName, jobGroup));
    }

    public void rescheduleCron(String jobName, String jobGroup, String triggerName, String triggerGroup, String cronExpression) throws SchedulerException {
        rescheduleCron(jobName, jobGroup, triggerName, triggerGroup, cronExpression,
                defaultTimeZone, MisfirePolicy.SMART_POLICY);
    }

    public void rescheduleCron(String jobName, String jobGroup, String triggerName, String triggerGroup,
                               String cronExpression, String timeZone,
                               MisfirePolicy misfirePolicy) throws SchedulerException {
        assertDefinitionIsMutable(JobKey.jobKey(jobName, jobGroup));
        TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
        Trigger newTrigger = QuartzTriggerFactory.build(
                triggerName, triggerGroup, cronExpression, 0, timeZone, misfirePolicy,
                null, null, JobKey.jobKey(jobName, jobGroup));
        scheduler.rescheduleJob(triggerKey, newTrigger);
    }

    public void rescheduleInterval(String jobName, String jobGroup, String triggerName, String triggerGroup, int intervalSeconds) throws SchedulerException {
        rescheduleInterval(jobName, jobGroup, triggerName, triggerGroup, intervalSeconds,
                MisfirePolicy.SMART_POLICY);
    }

    public void rescheduleInterval(String jobName, String jobGroup, String triggerName, String triggerGroup,
                                   int intervalSeconds, MisfirePolicy misfirePolicy) throws SchedulerException {
        assertDefinitionIsMutable(JobKey.jobKey(jobName, jobGroup));
        TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
        Trigger newTrigger = QuartzTriggerFactory.build(
                triggerName, triggerGroup, "", intervalSeconds,
                CoQuartzConstants.DEFAULT_TIME_ZONE, misfirePolicy,
                null, null, JobKey.jobKey(jobName, jobGroup));
        scheduler.rescheduleJob(triggerKey, newTrigger);
    }

    private void assertDefinitionIsMutable(JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail != null && Boolean.parseBoolean(jobDetail.getJobDataMap()
                .getString(CoQuartzConstants.CODE_OWNED))) {
            throw new CodeOwnedTaskModificationException("Task " + jobKey
                    + " is owned by application code; modify its code definition instead of using the management API");
        }
    }
}
