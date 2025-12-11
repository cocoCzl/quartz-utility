package com.coco.core;

import com.coco.enums.TimeEnum;

public class QuartzComponent {

    // 任务标识设置
    private final String description;
    // 任务恢复设置
    private final boolean shouldRecover;
    // 时间间隔
    private final int timeInterval;
    // 间隔时间单位
    private final TimeEnum timeEnum;
    // 任务持久化设置
    private final boolean durability;
    // Cron 表达式（如果使用 Cron 触发器）
    private final String cronExpression;
    // 是否使用 Cron 触发器
    private final boolean useCronTrigger;
    // misfire 策略（针对简单触发器）
    private final int misfireInstruction;

    public String getDescription() {
        return description;
    }

    public boolean isShouldRecover() {
        return shouldRecover;
    }

    public int getTimeInterval() {
        return timeInterval;
    }

    public TimeEnum getTimeEnum() {
        return timeEnum;
    }

    public boolean isDurability() {
        return durability;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public boolean isUseCronTrigger() {
        return useCronTrigger;
    }

    public int getMisfireInstruction() {
        return misfireInstruction;
    }

    private QuartzComponent(Builder builder) {
        this.timeInterval = builder.timeInterval;
        this.timeEnum = builder.timeEnum;
        this.description = builder.description;
        this.shouldRecover = builder.shouldRecover;
        this.durability = builder.durability;
        this.cronExpression = builder.cronExpression;
        this.useCronTrigger = builder.useCronTrigger;
        this.misfireInstruction = builder.misfireInstruction;
    }

    public static class Builder {

        private int timeInterval = 5;
        private TimeEnum timeEnum = TimeEnum.MINUTES;
        private String description = "Default description";
        private boolean shouldRecover = false;
        private boolean durability = true;
        private String cronExpression = null;
        private boolean useCronTrigger = false;
        private int misfireInstruction = -1; // -1 表示使用默认值

        public Builder setTimeInterval(int timeInterval) {
            this.timeInterval = timeInterval;
            return this;
        }

        public Builder setTimeEnum(TimeEnum timeEnum) {
            this.timeEnum = timeEnum;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setShouldRecover(boolean shouldRecover) {
            this.shouldRecover = shouldRecover;
            return this;
        }

        public Builder setDurability(boolean durability) {
            this.durability = durability;
            return this;
        }

        /**
         * 设置 Cron 表达式，设置此参数将使用 CronTrigger 而非 SimpleTrigger
         */
        public Builder setCronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
            this.useCronTrigger = true;
            return this;
        }

        /**
         * 设置 misfire 策略
         */
        public Builder setMisfireInstruction(int misfireInstruction) {
            this.misfireInstruction = misfireInstruction;
            return this;
        }

        public QuartzComponent build() {
            return new QuartzComponent(this);
        }
    }
}
