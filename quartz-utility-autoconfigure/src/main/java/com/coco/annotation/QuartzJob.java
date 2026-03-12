package com.coco.annotation;

import java.lang.annotation.*;

/**
 * Quartz 任务注解
 * 使用此注解标记的任务会自动注册到 Quartz 调度器中
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QuartzJob {

    /**
     * 任务名称（必须）
     */
    String name();

    /**
     * 任务组名（可选，默认为 DEFAULT）
     */
    String group() default "DEFAULT";

    /**
     * 任务描述
     */
    String description() default "";

    /**
     * Cron 表达式（与 intervalSeconds 二选一）
     */
    String cron() default "";

    /**
     * 固定间隔时间（秒）（与 cron 二选一）
     */
    int intervalSeconds() default 0;

    /**
     * 是否持久化
     */
    boolean durable() default true;

    /**
     * 任务失败时是否恢复
     */
    boolean recoverable() default false;

    /**
     * 失败重试次数
     */
    int retryTimes() default 0;

    /**
     * 重试间隔时间（毫秒）
     */
    long retryInterval() default 1000;

    /**
     * 任务超时时间（毫秒），0 表示不限制
     */
    long timeout() default 0;

    /**
     * 是否启用
     */
    boolean enabled() default true;

    /**
     * Misfire 策略
     */
    MisfirePolicy misfirePolicy() default MisfirePolicy.SMART_POLICY;

    /**
     * Misfire 策略枚举
     */
    enum MisfirePolicy {
        /**
         * 使用智能策略
         */
        SMART_POLICY(-1),
        /**
         * 立即执行
         */
        FIRE_NOW(1),
        /**
         * 忽略
         */
        IGNORE_MISFIRE_POLICY(2);

        private final int code;

        MisfirePolicy(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
