package io.github.cococzl.coquartz.core;

import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Date;
import java.util.List;
import java.util.Set;

public class CoQuartzScheduler {

    private final Scheduler scheduler;

    public CoQuartzScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Date scheduleJob(JobDetail jobDetail, Trigger trigger) throws SchedulerException {
        return scheduler.scheduleJob(jobDetail, trigger);
    }

    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.pauseJob(SchedulerCore.getJobKey(jobName, jobGroup));
    }

    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.resumeJob(SchedulerCore.getJobKey(jobName, jobGroup));
    }

    public boolean deleteJob(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.deleteJob(SchedulerCore.getJobKey(jobName, jobGroup));
    }

    public void triggerJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.triggerJob(SchedulerCore.getJobKey(jobName, jobGroup));
    }

    public JobDetail getJobDetail(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.getJobDetail(SchedulerCore.getJobKey(jobName, jobGroup));
    }

    public boolean checkExists(JobKey jobKey) throws SchedulerException {
        return scheduler.checkExists(jobKey);
    }

    public List<String> getJobGroupNames() throws SchedulerException {
        return scheduler.getJobGroupNames();
    }

    public Set<JobKey> getJobKeys(GroupMatcher<JobKey> matcher) throws SchedulerException {
        return scheduler.getJobKeys(matcher);
    }
}