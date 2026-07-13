package io.github.cococzl.coquartz.annotation;

import io.github.cococzl.coquartz.enums.MisfirePolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Quartz task on a public, no-argument Spring bean method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QuartzTask {

    String name() default "";

    String group() default "DEFAULT";

    String description() default "";

    String cron() default "";

    int intervalSeconds() default 0;

    String timeZone() default "";

    boolean concurrent() default false;

    boolean enabled() default true;

    MisfirePolicy misfirePolicy() default MisfirePolicy.SMART_POLICY;

    int retryTimes() default 0;

    long retryInterval() default 1000;

    boolean exponentialBackoff() default false;

    double backoffMultiplier() default 1.5;

    long timeout() default 0;
}
