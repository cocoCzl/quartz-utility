package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.dto.TaskInfo;
import io.github.cococzl.coquartz.dto.TaskSource;
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
        return listJobs(1000);
    }

    public List<TaskInfo> listJobs(int limit) throws SchedulerException {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Task limit must be 1..1000");
        List<TaskInfo> result = new ArrayList<>();
        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyGroup());
        for (JobKey jobKey : jobKeys.stream().sorted().limit(limit).toList()) {
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
        String delegate = jobDetail.getJobDataMap().getString(CoQuartzConstants.DELEGATE_JOB_CLASS);
        info.setJobClassName(delegate == null ? jobDetail.getJobClass().getName() : delegate);
        String bean = jobDetail.getJobDataMap().getString(CoQuartzConstants.METHOD_BEAN);
        if (bean != null) info.setMethodSource(bean + "#" + jobDetail.getJobDataMap().getString(CoQuartzConstants.METHOD_NAME));
        info.setDescription(jobDetail.getDescription());
        info.setDurable(jobDetail.isDurable());
        info.setRecoverable(jobDetail.requestsRecovery());
        info.setSource(resolveSource(jobDetail));

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobDetail.getKey());
        String mainKey = jobDetail.getJobDataMap().getString(CoQuartzConstants.MAIN_TRIGGER_KEY);
        if (mainKey == null) mainKey = TriggerKey.triggerKey(CoQuartzConstants.TRIGGER_KEY_PREFIX + jobName, jobGroup).toString();
        String desiredMain = mainKey;
        Trigger main = triggers.stream().map(trigger -> (Trigger) trigger).filter(trigger -> desiredMain.equals(trigger.getKey().toString())).findFirst()
                .orElseGet(() -> triggers.stream().filter(trigger -> !temporary(trigger))
                        .sorted(java.util.Comparator.comparing(trigger -> trigger.getKey().toString())).findFirst().orElse(null));
        List<io.github.cococzl.coquartz.dto.TaskTriggerInfo> triggerInfos = new ArrayList<>();
        for (Trigger trigger : triggers.stream().sorted(java.util.Comparator.comparing(t -> t.getKey().toString())).toList()) {
            String purpose = trigger == main ? "PRIMARY" : CoQuartzConstants.RETRY_TRIGGER_GROUP.equals(trigger.getKey().getGroup())
                    ? "RETRY" : Scheduler.DEFAULT_RECOVERY_GROUP.equals(trigger.getKey().getGroup()) ? "RECOVERY"
                    : temporary(trigger) ? "MANUAL" : "EXTERNAL";
            triggerInfos.add(new io.github.cococzl.coquartz.dto.TaskTriggerInfo(trigger.getKey().getName(),
                    trigger.getKey().getGroup(), purpose, scheduler.getTriggerState(trigger.getKey()).name(), trigger.getNextFireTime()));
        }
        info.setTriggers(triggerInfos);
        if (main != null) {
            Trigger trigger = main;
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

    private boolean temporary(Trigger trigger) {
        return CoQuartzConstants.RETRY_TRIGGER_GROUP.equals(trigger.getKey().getGroup())
                || Scheduler.DEFAULT_RECOVERY_GROUP.equals(trigger.getKey().getGroup())
                || trigger instanceof SimpleTrigger simple && simple.getRepeatCount() == 0;
    }

    private TaskSource resolveSource(JobDetail jobDetail) {
        if (Boolean.parseBoolean(jobDetail.getJobDataMap()
                .getString(CoQuartzConstants.CODE_OWNED))) {
            return TaskSource.DECLARATIVE;
        }
        if (CoQuartzConstants.OWNER_VALUE.equals(jobDetail.getJobDataMap()
                .getString(CoQuartzConstants.OWNER))
                && CoQuartzConstants.SOURCE_DYNAMIC.equals(jobDetail.getJobDataMap()
                .getString(CoQuartzConstants.TASK_SOURCE))) {
            return TaskSource.DYNAMIC;
        }
        return TaskSource.EXTERNAL;
    }

    /** Current Scheduler node only; never a cluster-wide view. */
    public List<String> getRunningJobs() throws SchedulerException { return getLocalRunningJobs(); }

    public List<String> getLocalRunningJobs() throws SchedulerException {
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
