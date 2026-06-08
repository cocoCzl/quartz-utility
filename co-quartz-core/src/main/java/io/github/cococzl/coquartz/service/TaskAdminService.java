package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskInfo;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TaskAdminService {

    private final Scheduler scheduler;

    public TaskAdminService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey(jobName, jobGroup));
    }

    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey(jobName, jobGroup));
    }

    public boolean deleteJob(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.deleteJob(JobKey.jobKey(jobName, jobGroup));
    }

    public void triggerNow(String jobName, String jobGroup) throws SchedulerException {
        scheduler.triggerJob(JobKey.jobKey(jobName, jobGroup));
    }

    public boolean exists(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.checkExists(JobKey.jobKey(jobName, jobGroup));
    }

    public void rescheduleCron(String jobName, String jobGroup, String triggerName, String triggerGroup, String cronExpression) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
        CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
        Trigger newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(cronBuilder)
                .build();
        scheduler.rescheduleJob(triggerKey, newTrigger);
    }

    public void rescheduleInterval(String jobName, String jobGroup, String triggerName, String triggerGroup, int intervalSeconds) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, triggerGroup);
        SimpleScheduleBuilder simpleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever();
        Trigger newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(simpleBuilder)
                .build();
        scheduler.rescheduleJob(triggerKey, newTrigger);
    }
}