package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.exception.CodeOwnedTaskModificationException;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Date;
import java.util.List;
import java.util.Set;

public class CoQuartzScheduler {

    private final Scheduler scheduler;
    private final String defaultTimeZone;

    public CoQuartzScheduler(Scheduler scheduler) {
        this(scheduler, CoQuartzConstants.DEFAULT_TIME_ZONE);
    }

    public CoQuartzScheduler(Scheduler scheduler, CoQuartzProperties properties) {
        this(scheduler, properties.getScheduling().getDefaultTimeZone());
    }

    private CoQuartzScheduler(Scheduler scheduler, String defaultTimeZone) {
        this.scheduler = scheduler;
        this.defaultTimeZone = defaultTimeZone;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public String getDefaultTimeZone() {
        return defaultTimeZone;
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
        JobKey jobKey = SchedulerCore.getJobKey(jobName, jobGroup);
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail != null && Boolean.parseBoolean(jobDetail.getJobDataMap()
                .getString(CoQuartzConstants.CODE_OWNED))) {
            throw new CodeOwnedTaskModificationException("Task " + jobKey
                    + " is owned by application code; modify its code definition instead of using the management API");
        }
        return scheduler.deleteJob(jobKey);
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
