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

    private QuartzComponent(Builder builder) {
        this.timeInterval = builder.timeInterval;
        this.timeEnum = builder.timeEnum;
        this.description = builder.description;
        this.shouldRecover = builder.shouldRecover;
        this.durability = builder.durability;
    }

    public static class Builder {

        private int timeInterval = 5;
        private TimeEnum timeEnum = TimeEnum.HOURS;
        private String description = "Default description";
        private boolean shouldRecover = false;
        private boolean durability = true;

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

        public QuartzComponent build() {
            return new QuartzComponent(this);
        }
    }
}
