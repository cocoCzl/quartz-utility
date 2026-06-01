package com.coco.service;

import com.coco.core.CoQuartzScheduler;
import com.coco.core.QuartzComponent;
import com.coco.core.QuartzTaskBuilder;
import com.coco.core.SchedulerCore;
import com.coco.dto.TaskScheduleRequest;
import com.coco.enums.TimeEnum;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.List;

/**
 * Business-facing task administration service.
 */
public class TaskAdminService {

    private final CoQuartzScheduler scheduler;

    public TaskAdminService(CoQuartzScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void schedule(TaskScheduleRequest request) throws SchedulerException {
        QuartzTaskBuilder builder = QuartzTaskBuilder.newBuilder()
                .jobClass(request.getJobClass())
                .jobName(request.getJobName())
                .jobGroup(request.getJobGroup())
                .triggerGroup(request.getTriggerGroup())
                .description(request.getDescription())
                .durable(request.isDurable())
                .recoverable(request.isRecoverable())
                .retryTimes(request.getRetryTimes())
                .retryInterval(request.getRetryInterval())
                .exponentialBackoff(request.isExponentialBackoff())
                .backoffMultiplier(request.getBackoffMultiplier())
                .timeout(request.getTimeout());

        if (request.getTriggerName() != null && !request.getTriggerName().isBlank()) {
            builder.triggerName(request.getTriggerName());
        }
        if (request.getJobData() != null) {
            builder.jobData(request.getJobData());
        }
        if (request.getMisfireInstruction() != -1) {
            builder.misfireInstruction(request.getMisfireInstruction());
        }
        if (request.getCronExpression() != null && !request.getCronExpression().isBlank()) {
            builder.cron(request.getCronExpression());
        } else if (request.getIntervalSeconds() != null) {
            builder.intervalInSeconds(request.getIntervalSeconds());
        }

        builder.schedule(scheduler);
    }

    public void triggerNow(String jobName, String group) throws SchedulerException {
        scheduler.triggerJob(jobKey(jobName, group));
    }

    public void triggerNow(String jobName, String group, JobDataMap jobDataMap) throws SchedulerException {
        scheduler.triggerJob(jobKey(jobName, group), jobDataMap);
    }

    public void pause(String jobName, String group) throws SchedulerException {
        scheduler.pauseJob(jobKey(jobName, group));
    }

    public void resume(String jobName, String group) throws SchedulerException {
        scheduler.resumeJob(jobKey(jobName, group));
    }

    public boolean delete(String jobName, String group) throws SchedulerException {
        return scheduler.deleteJob(jobKey(jobName, group));
    }

    public boolean exists(String jobName, String group) throws SchedulerException {
        return scheduler.checkExists(jobKey(jobName, group));
    }

    public void rescheduleCron(String jobName, String group, String cronExpression) throws SchedulerException {
        JobKey jobKey = jobKey(jobName, group);
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail == null) {
            throw new SchedulerException("Job does not exist: " + jobKey);
        }
        TriggerKey triggerKey = primaryTriggerKey(jobKey);
        QuartzComponent component = baseComponent(jobDetail)
                .setCronExpression(cronExpression)
                .build();
        scheduler.scheduleCronJob(jobDetail.getJobClass(), jobKey, triggerKey,
                jobDetail.getJobDataMap(), null, component);
    }

    public void rescheduleInterval(String jobName, String group, int intervalSeconds) throws SchedulerException {
        JobKey jobKey = jobKey(jobName, group);
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail == null) {
            throw new SchedulerException("Job does not exist: " + jobKey);
        }
        TriggerKey triggerKey = primaryTriggerKey(jobKey);
        QuartzComponent component = baseComponent(jobDetail)
                .setTimeEnum(TimeEnum.SECONDS)
                .setTimeInterval(intervalSeconds)
                .build();
        scheduler.scheduleSimpleIntervalJob(jobDetail.getJobClass(), jobKey, triggerKey,
                jobDetail.getJobDataMap(), null, component);
    }

    private TriggerKey primaryTriggerKey(JobKey jobKey) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers.isEmpty()) {
            return SchedulerCore.getTriggerKey(jobKey.getName(), jobKey.getGroup());
        }
        return triggers.get(0).getKey();
    }

    private QuartzComponent.Builder baseComponent(JobDetail jobDetail) {
        return new QuartzComponent.Builder()
                .setDescription(jobDetail.getDescription())
                .setDurability(jobDetail.isDurable())
                .setShouldRecover(jobDetail.requestsRecovery());
    }

    private JobKey jobKey(String jobName, String group) {
        return SchedulerCore.getJobKey(jobName, group);
    }
}
