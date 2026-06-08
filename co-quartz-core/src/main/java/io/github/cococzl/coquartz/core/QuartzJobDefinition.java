package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import io.github.cococzl.coquartz.enums.MisfirePolicy;
import org.quartz.Job;

import java.util.HashMap;
import java.util.Map;

public class QuartzJobDefinition {

    private final String name;
    private final String group;
    private final String description;
    private final String cronExpression;
    private final int intervalSeconds;
    private final boolean concurrent;
    private final boolean durable;
    private final boolean recoverable;
    private final boolean enabled;
    private final MisfirePolicy misfirePolicy;
    private final int retryTimes;
    private final long retryInterval;
    private final boolean exponentialBackoff;
    private final double backoffMultiplier;
    private final long timeout;
    private final Class<? extends Job> jobClass;

    private QuartzJobDefinition(Builder builder) {
        this.name = builder.name;
        this.group = builder.group;
        this.description = builder.description;
        this.cronExpression = builder.cronExpression;
        this.intervalSeconds = builder.intervalSeconds;
        this.concurrent = builder.concurrent;
        this.durable = builder.durable;
        this.recoverable = builder.recoverable;
        this.enabled = builder.enabled;
        this.misfirePolicy = builder.misfirePolicy;
        this.retryTimes = builder.retryTimes;
        this.retryInterval = builder.retryInterval;
        this.exponentialBackoff = builder.exponentialBackoff;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.timeout = builder.timeout;
        this.jobClass = builder.jobClass;
    }

    @SuppressWarnings("unchecked")
    public static QuartzJobDefinition fromAnnotation(QuartzJob annotation, Class<?> jobClass) {
        return new Builder()
                .name(annotation.name())
                .group(annotation.group())
                .description(annotation.description())
                .cronExpression(annotation.cron())
                .intervalSeconds(annotation.intervalSeconds())
                .concurrent(annotation.concurrent())
                .durable(annotation.durable())
                .recoverable(annotation.recoverable())
                .enabled(annotation.enabled())
                .misfirePolicy(annotation.misfirePolicy())
                .retryTimes(annotation.retryTimes())
                .retryInterval(annotation.retryInterval())
                .exponentialBackoff(annotation.exponentialBackoff())
                .backoffMultiplier(annotation.backoffMultiplier())
                .timeout(annotation.timeout())
                .jobClass((Class<? extends Job>) jobClass)
                .build();
    }

    public Map<String, Object> toJobDataMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(CoQuartzConstants.ENHANCED, true);
        map.put(CoQuartzConstants.RETRY_TIMES, retryTimes);
        map.put(CoQuartzConstants.RETRY_INTERVAL, retryInterval);
        map.put(CoQuartzConstants.EXPONENTIAL_BACKOFF, exponentialBackoff);
        map.put(CoQuartzConstants.BACKOFF_MULTIPLIER, backoffMultiplier);
        map.put(CoQuartzConstants.TIMEOUT, timeout);
        map.put(CoQuartzConstants.CONCURRENT, concurrent);
        map.put(CoQuartzConstants.MISFIRE_POLICY, misfirePolicy.name());
        return map;
    }

    public String getName() { return name; }
    public String getGroup() { return group; }
    public String getDescription() { return description; }
    public String getCronExpression() { return cronExpression; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public boolean isConcurrent() { return concurrent; }
    public boolean isDurable() { return durable; }
    public boolean isRecoverable() { return recoverable; }
    public boolean isEnabled() { return enabled; }
    public MisfirePolicy getMisfirePolicy() { return misfirePolicy; }
    public int getRetryTimes() { return retryTimes; }
    public long getRetryInterval() { return retryInterval; }
    public boolean isExponentialBackoff() { return exponentialBackoff; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getTimeout() { return timeout; }
    public Class<? extends Job> getJobClass() { return jobClass; }

    public static class Builder {
        private String name = "";
        private String group = "DEFAULT";
        private String description = "";
        private String cronExpression = "";
        private int intervalSeconds = 0;
        private boolean concurrent = false;
        private boolean durable = false;
        private boolean recoverable = false;
        private boolean enabled = true;
        private MisfirePolicy misfirePolicy = MisfirePolicy.SMART_POLICY;
        private int retryTimes = 0;
        private long retryInterval = 1000;
        private boolean exponentialBackoff = false;
        private double backoffMultiplier = 1.5;
        private long timeout = 0;
        private Class<? extends Job> jobClass;

        public Builder name(String name) { this.name = name; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder cronExpression(String cronExpression) { this.cronExpression = cronExpression; return this; }
        public Builder intervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; return this; }
        public Builder concurrent(boolean concurrent) { this.concurrent = concurrent; return this; }
        public Builder durable(boolean durable) { this.durable = durable; return this; }
        public Builder recoverable(boolean recoverable) { this.recoverable = recoverable; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder misfirePolicy(MisfirePolicy misfirePolicy) { this.misfirePolicy = misfirePolicy; return this; }
        public Builder retryTimes(int retryTimes) { this.retryTimes = retryTimes; return this; }
        public Builder retryInterval(long retryInterval) { this.retryInterval = retryInterval; return this; }
        public Builder exponentialBackoff(boolean exponentialBackoff) { this.exponentialBackoff = exponentialBackoff; return this; }
        public Builder backoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; return this; }
        public Builder timeout(long timeout) { this.timeout = timeout; return this; }
        public Builder jobClass(Class<? extends Job> jobClass) { this.jobClass = jobClass; return this; }

        public QuartzJobDefinition build() {
            return new QuartzJobDefinition(this);
        }
    }
}