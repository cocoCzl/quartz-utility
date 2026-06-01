package com.coco.service;

import com.coco.core.CoQuartzScheduler;
import com.coco.core.SchedulerCore;
import com.coco.dto.TaskInfo;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Business-facing task query service.
 */
public class TaskQueryService {

    private final CoQuartzScheduler scheduler;

    public TaskQueryService(CoQuartzScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public List<TaskInfo> listJobs() throws SchedulerException {
        List<TaskInfo> result = new ArrayList<>();
        for (JobKey jobKey : scheduler.getAllJobKeys()) {
            result.add(getJobDetail(jobKey));
        }
        return result;
    }

    public TaskInfo getJobDetail(String jobName, String group) throws SchedulerException {
        return getJobDetail(SchedulerCore.getJobKey(jobName, group));
    }

    public TaskInfo getJobDetail(JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail == null) {
            return null;
        }

        TaskInfo info = mapJob(jobDetail);
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (!triggers.isEmpty()) {
            fillTrigger(info, triggers.get(0));
        }
        return info;
    }

    public Trigger.TriggerState getTriggerState(String triggerName, String group) throws SchedulerException {
        return scheduler.getTriggerState(SchedulerCore.getTriggerKey(triggerName, group));
    }

    public java.util.Date getNextFireTime(String triggerName, String group) throws SchedulerException {
        return scheduler.getNextFireTime(SchedulerCore.getTriggerKey(triggerName, group));
    }

    public java.util.Date getPreviousFireTime(String triggerName, String group) throws SchedulerException {
        return scheduler.getPreviousFireTime(SchedulerCore.getTriggerKey(triggerName, group));
    }

    public List<TaskInfo> getRunningJobs() throws SchedulerException {
        List<TaskInfo> result = new ArrayList<>();
        for (JobExecutionContext context : scheduler.scheduler().getCurrentlyExecutingJobs()) {
            TaskInfo info = mapJob(context.getJobDetail());
            fillTrigger(info, context.getTrigger());
            result.add(info);
        }
        return result;
    }

    private TaskInfo mapJob(JobDetail jobDetail) {
        TaskInfo info = new TaskInfo();
        info.setJobName(jobDetail.getKey().getName());
        info.setJobGroup(jobDetail.getKey().getGroup());
        info.setJobClassName(jobDetail.getJobClass().getName());
        info.setDescription(jobDetail.getDescription());
        info.setDurable(jobDetail.isDurable());
        info.setRecoverable(jobDetail.requestsRecovery());
        info.setJobData(new HashMap<>(jobDetail.getJobDataMap()));
        return info;
    }

    private void fillTrigger(TaskInfo info, Trigger trigger) throws SchedulerException {
        TriggerKey key = trigger.getKey();
        info.setTriggerName(key.getName());
        info.setTriggerGroup(key.getGroup());
        info.setTriggerType(trigger.getClass().getSimpleName());
        info.setPreviousFireTime(trigger.getPreviousFireTime());
        info.setNextFireTime(trigger.getNextFireTime());
        info.setTriggerState(scheduler.getTriggerState(key).name());

        if (trigger instanceof CronTrigger cronTrigger) {
            info.setCronExpression(cronTrigger.getCronExpression());
        }
        if (trigger instanceof SimpleTrigger simpleTrigger) {
            info.setRepeatIntervalMs(simpleTrigger.getRepeatInterval());
        }
    }
}
