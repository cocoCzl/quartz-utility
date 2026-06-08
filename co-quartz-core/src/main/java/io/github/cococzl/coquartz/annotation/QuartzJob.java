package io.github.cococzl.coquartz.annotation;

import io.github.cococzl.coquartz.enums.MisfirePolicy;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QuartzJob {

    String name() default "";

    String group() default "DEFAULT";

    String description() default "";

    String cron() default "";

    int intervalSeconds() default 0;

    boolean concurrent() default false;

    boolean durable() default false;

    boolean recoverable() default false;

    boolean enabled() default true;

    MisfirePolicy misfirePolicy() default MisfirePolicy.SMART_POLICY;

    int retryTimes() default 0;

    long retryInterval() default 1000;

    boolean exponentialBackoff() default false;

    double backoffMultiplier() default 1.5;

    long timeout() default 0;
}