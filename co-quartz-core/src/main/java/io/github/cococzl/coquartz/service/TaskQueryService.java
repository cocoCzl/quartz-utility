package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskInfo;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class TaskQueryService {

    private final Scheduler scheduler;

    public TaskQueryService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public List<TaskInfo> listJobs() throws SchedulerException {
        List<TaskInfo> result = new ArrayList<>();
        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup());
        for (JobKey jobKey : jobKeys) {
            TaskInfo info = getJobDetail(jobKey.getName(), jobKey.getGroup());
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    public TaskInfo getJobDetail(String jobName, String jobGroup) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(jobName, jobGroup));
        if (jobDetail == null) {
            return null;
        }

        TaskInfo info = new TaskInfo();
        info.setJobName(jobDetail.getKey().getName());
        info.setJobGroup(jobDetail.getKey().getGroup());
        info.setJobClassName(jobDetail.getJobClass().getName());
        info.setDescription(jobDetail.getDescription());
        info.setDurable(jobDetail.isDurable());
        info.setRecoverable(jobDetail.requestsRecovery());
        info.setJobData(jobDetail.getJobDataMap().getWrappedMap());

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobDetail.getKey());
        if (!triggers.isEmpty()) {
            Trigger trigger = triggers.get(0);
            info.setTriggerName(trigger.getKey().getName());
            info.setTriggerGroup(trigger.getKey().getGroup());
            info.setPreviousFireTime(trigger.getPreviousFireTime());
            info.setNextFireTime(trigger.getNextFireTime());

            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
            info.setTriggerState(state.name());

            if (trigger instanceof CronTrigger cronTrigger) {
                info.setCronExpression(cronTrigger.getCronExpression());
                info.setTriggerType("CRON");
            } else if (trigger instanceof SimpleTrigger simpleTrigger) {
                info.setRepeatIntervalMs(simpleTrigger.getRepeatInterval());
                info.setTriggerType("SIMPLE");
            }
        }

        return info;
    }

    public List<String> getRunningJobs() throws SchedulerException {
        List<String> result = new ArrayList<>();
        for (JobExecutionContext context : scheduler.getCurrentlyExecutingJobs()) {
            result.add(context.getJobDetail().getKey().toString());
        }
        return result;
    }

    public Trigger.TriggerState getTriggerState(String triggerName, String triggerGroup) throws SchedulerException {
        return scheduler.getTriggerState(TriggerKey.triggerKey(triggerName, triggerGroup));
    }

    public Date getNextFireTime(String triggerName, String triggerGroup) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(triggerName, triggerGroup));
        return trigger != null ? trigger.getNextFireTime() : null;
    }

    public Date getPreviousFireTime(String triggerName, String triggerGroup) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(triggerName, triggerGroup));
        return trigger != null ? trigger.getPreviousFireTime() : null;
    }
}