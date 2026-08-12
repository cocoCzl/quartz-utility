package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import io.github.cococzl.coquartz.exception.CoQuartzSchedulingException;
import org.quartz.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class QuartzTaskBuilder {

    private Class<? extends Job> jobClass;
    private String jobName;
    private String jobGroup = CoQuartzConstants.DEFAULT_GROUP;
    private String triggerName;
    private String triggerGroup = CoQuartzConstants.DEFAULT_GROUP;
    private String description;
    private Map<String, Object> jobData = new HashMap<>();
    private String cronExpression;
    private int intervalSeconds;
    private String timeZone;
    private boolean durable = false;
    private boolean recoverable = false;
    private int retryTimes = 0;
    private long retryInterval = 1000;
    private boolean exponentialBackoff = false;
    private double backoffMultiplier = 1.5;
    private long timeout = 0;
    private boolean concurrent = false;
    private MisfirePolicy misfirePolicy = MisfirePolicy.SMART_POLICY;
    private Date startAt;
    private Date endAt;

    private QuartzTaskBuilder() {
    }

    public static QuartzTaskBuilder newBuilder() {
        return new QuartzTaskBuilder();
    }

    public QuartzTaskBuilder jobClass(Class<? extends Job> jobClass) {
        this.jobClass = jobClass;
        return this;
    }

    public QuartzTaskBuilder jobName(String jobName) {
        this.jobName = jobName;
        return this;
    }

    public QuartzTaskBuilder jobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
        return this;
    }

    public QuartzTaskBuilder triggerName(String triggerName) {
        this.triggerName = triggerName;
        return this;
    }

    public QuartzTaskBuilder triggerGroup(String triggerGroup) {
        this.triggerGroup = triggerGroup;
        return this;
    }

    public QuartzTaskBuilder description(String description) {
        this.description = description;
        return this;
    }

    public QuartzTaskBuilder jobData(String key, Object value) {
        this.jobData.put(key, value);
        return this;
    }

    public QuartzTaskBuilder jobData(Map<String, Object> jobData) {
        this.jobData.putAll(jobData);
        return this;
    }

    public QuartzTaskBuilder cron(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    public QuartzTaskBuilder intervalInSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
        return this;
    }

    public QuartzTaskBuilder timeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    public QuartzTaskBuilder durable(boolean durable) {
        this.durable = durable;
        return this;
    }

    public QuartzTaskBuilder recoverable(boolean recoverable) {
        this.recoverable = recoverable;
        return this;
    }

    public QuartzTaskBuilder retryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
        return this;
    }

    public QuartzTaskBuilder retryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
        return this;
    }

    public QuartzTaskBuilder exponentialBackoff(boolean exponentialBackoff) {
        this.exponentialBackoff = exponentialBackoff;
        return this;
    }

    public QuartzTaskBuilder backoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
        return this;
    }

    public QuartzTaskBuilder timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public QuartzTaskBuilder concurrent(boolean concurrent) {
        this.concurrent = concurrent;
        return this;
    }

    public QuartzTaskBuilder misfirePolicy(MisfirePolicy misfirePolicy) {
        this.misfirePolicy = misfirePolicy;
        return this;
    }

    public QuartzTaskBuilder startAt(Date startAt) {
        this.startAt = startAt;
        return this;
    }

    public QuartzTaskBuilder endAt(Date endAt) {
        this.endAt = endAt;
        return this;
    }

    public Date schedule(Scheduler scheduler) throws CoQuartzSchedulingException {
        try {
            JobDetail jobDetail = buildJobDetail();
            Trigger trigger = buildTrigger();

            if (scheduler.checkExists(jobDetail.getKey())) {
                scheduler.addJob(jobDetail, true);
                Date rescheduled = scheduler.rescheduleJob(trigger.getKey(), trigger);
                if (rescheduled == null) {
                    return scheduler.scheduleJob(trigger);
                }
            } else {
                return scheduler.scheduleJob(jobDetail, trigger);
            }
            return trigger.getNextFireTime();
        } catch (SchedulerException e) {
            throw new CoQuartzSchedulingException("Failed to schedule job: " + jobName, e);
        }
    }

    public Date schedule(CoQuartzScheduler scheduler) throws CoQuartzSchedulingException {
        String originalTimeZone = timeZone;
        try {
            if (timeZone == null || timeZone.isBlank()) {
                timeZone = scheduler.getDefaultTimeZone();
            }
            return schedule(scheduler.getScheduler());
        } finally {
            timeZone = originalTimeZone;
        }
    }

    public Date executeNow(Scheduler scheduler) throws CoQuartzSchedulingException {
        try {
            JobDetail jobDetail = buildJobDetail();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerName != null ? triggerName : "TRIGGER_NOW_" + jobName, triggerGroup)
                    .startNow()
                    .forJob(jobDetail.getKey())
                    .build();

            if (!scheduler.checkExists(jobDetail.getKey())) {
                return scheduler.scheduleJob(jobDetail, trigger);
            }
            return scheduler.scheduleJob(trigger);
        } catch (SchedulerException e) {
            throw new CoQuartzSchedulingException("Failed to execute job now: " + jobName, e);
        }
    }

    private JobDetail buildJobDetail() {
        if (jobClass == null) {
            throw new CoQuartzSchedulingException("jobClass must be specified");
        }
        if (jobName == null || jobName.isEmpty()) {
            jobName = jobClass.getSimpleName();
        }

        Map<String, Object> allData = new HashMap<>(jobData);
        allData.put(CoQuartzConstants.OWNER, CoQuartzConstants.OWNER_VALUE);
        allData.put(CoQuartzConstants.CODE_OWNED, "false");
        allData.put(CoQuartzConstants.METADATA_VERSION, CoQuartzConstants.METADATA_VERSION_VALUE);
        allData.put(CoQuartzConstants.TASK_SOURCE, CoQuartzConstants.SOURCE_DYNAMIC);
        allData.put(CoQuartzConstants.ENHANCED, true);
        allData.put(CoQuartzConstants.RETRY_TIMES, retryTimes);
        allData.put(CoQuartzConstants.RETRY_INTERVAL, retryInterval);
        allData.put(CoQuartzConstants.EXPONENTIAL_BACKOFF, exponentialBackoff);
        allData.put(CoQuartzConstants.BACKOFF_MULTIPLIER, backoffMultiplier);
        allData.put(CoQuartzConstants.TIMEOUT, timeout);
        allData.put(CoQuartzConstants.CONCURRENT, concurrent);
        allData.put(CoQuartzConstants.MISFIRE_POLICY, misfirePolicy.name());
        allData.put(CoQuartzConstants.TIME_ZONE, effectiveTimeZone());
        if (!concurrent) {
            allData.put(CoQuartzConstants.DELEGATE_JOB_CLASS, jobClass.getName());
        }

        JobBuilder builder = JobBuilder.newJob(concurrent ? jobClass : NonConcurrentJobWrapper.class)
                .withIdentity(jobName, jobGroup)
                .storeDurably(durable)
                .requestRecovery(recoverable)
                .usingJobData(new JobDataMap(allData));

        if (description != null && !description.isEmpty()) {
            builder.withDescription(description);
        }

        return builder.build();
    }

    private Trigger buildTrigger() throws CoQuartzSchedulingException {
        String tName = triggerName != null ? triggerName : CoQuartzConstants.TRIGGER_KEY_PREFIX + jobName;
        try {
            return QuartzTriggerFactory.build(
                    tName,
                    triggerGroup,
                    cronExpression,
                    intervalSeconds,
                    effectiveTimeZone(),
                    misfirePolicy,
                    startAt,
                    endAt,
                    JobKey.jobKey(jobName, jobGroup));
        } catch (CoQuartzConfigurationException e) {
            throw new CoQuartzSchedulingException(e.getMessage(), e);
        }
    }

    private String effectiveTimeZone() {
        return timeZone == null || timeZone.isBlank()
                ? CoQuartzConstants.DEFAULT_TIME_ZONE
                : timeZone;
    }
}
