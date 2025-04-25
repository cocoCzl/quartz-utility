package com.coco.core;

public class QuartzComponent {

    // 任务标识设置
    private final String description;
    // 任务恢复设置
    private final boolean shouldRecover;
    private final int minuteInterval;
    // 任务持久化设置
    private final boolean durability;

    public String getDescription() {
        return description;
    }

    public boolean isShouldRecover() {
        return shouldRecover;
    }

    public int getMinuteInterval() {
        return minuteInterval;
    }

    public boolean isDurability() {
        return durability;
    }

    private QuartzComponent(Builder builder) {
        this.minuteInterval = builder.minuteInterval;
        this.description = builder.description;
        this.shouldRecover = builder.shouldRecover;
        this.durability = builder.durability;
    }

    public static class Builder {

        private int minuteInterval = 5;
        private String description = "Default description";
        private boolean shouldRecover = false;
        private boolean durability = true;

        public Builder setMinuteInterval(int minuteInterval) {
            this.minuteInterval = minuteInterval;
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
