package com.coco.core;

import com.coco.annotation.QuartzJob;

/**
 * Quartz 任务定义
 * 存储从注解解析出的任务配置信息
 */
public class QuartzJobDefinition {

    private final String name;
    private final String group;
    private final String description;
    private final String cronExpression;
    private final int intervalSeconds;
    private final boolean durable;
    private final boolean recoverable;
    private final int retryTimes;
    private final long retryInterval;
    private final long timeout;
    private final boolean enabled;
    private final int misfireInstruction;
    private final Class<?> jobClass;

    private QuartzJobDefinition(Builder builder) {
        this.name = builder.name;
        this.group = builder.group;
        this.description = builder.description;
        this.cronExpression = builder.cronExpression;
        this.intervalSeconds = builder.intervalSeconds;
        this.durable = builder.durable;
        this.recoverable = builder.recoverable;
        this.retryTimes = builder.retryTimes;
        this.retryInterval = builder.retryInterval;
        this.timeout = builder.timeout;
        this.enabled = builder.enabled;
        this.misfireInstruction = builder.misfireInstruction;
        this.jobClass = builder.jobClass;
    }

    /**
     * 从注解创建任务定义
     */
    public static QuartzJobDefinition fromAnnotation(QuartzJob annotation, Class<?> jobClass) {
        return new Builder()
                .name(annotation.name())
                .group(annotation.group())
                .description(annotation.description())
                .cronExpression(annotation.cron())
                .intervalSeconds(annotation.intervalSeconds())
                .durable(annotation.durable())
                .recoverable(annotation.recoverable())
                .retryTimes(annotation.retryTimes())
                .retryInterval(annotation.retryInterval())
                .timeout(annotation.timeout())
                .enabled(annotation.enabled())
                .misfireInstruction(annotation.misfirePolicy().getCode())
                .jobClass(jobClass)
                .build();
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public long getTimeout() {
        return timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMisfireInstruction() {
        return misfireInstruction;
    }

    public Class<?> getJobClass() {
        return jobClass;
    }

    /**
     * 是否使用 Cron 表达式
     */
    public boolean isUseCron() {
        return cronExpression != null && !cronExpression.isEmpty();
    }

    public static class Builder {
        private String name;
        private String group = "DEFAULT";
        private String description = "";
        private String cronExpression;
        private int intervalSeconds;
        private boolean durable = true;
        private boolean recoverable = false;
        private int retryTimes;
        private long retryInterval = 1000;
        private long timeout;
        private boolean enabled = true;
        private int misfireInstruction = -1;
        private Class<?> jobClass;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder cronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
            return this;
        }

        public Builder intervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
            return this;
        }

        public Builder durable(boolean durable) {
            this.durable = durable;
            return this;
        }

        public Builder recoverable(boolean recoverable) {
            this.recoverable = recoverable;
            return this;
        }

        public Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        public Builder retryInterval(long retryInterval) {
            this.retryInterval = retryInterval;
            return this;
        }

        public Builder timeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder misfireInstruction(int misfireInstruction) {
            this.misfireInstruction = misfireInstruction;
            return this;
        }

        public Builder jobClass(Class<?> jobClass) {
            this.jobClass = jobClass;
            return this;
        }

        public QuartzJobDefinition build() {
            return new QuartzJobDefinition(this);
        }
    }
}
