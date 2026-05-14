package com.coco.example;

import com.coco.core.BaseAbstractQuartzJob;
import com.coco.core.CoQuartzScheduler;
import com.coco.core.QuartzTaskBuilder;
import com.coco.enums.TimeEnum;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用示例：如何使用 Quartz Utility（编程式）
 * 注意：此类不在生产 classpath 中，仅供参考。
 * 如需使用，请将此类复制到你的项目中并根据需要修改。
 */
public class QuartzUtilityExample {

    private static final Logger logger = LoggerFactory.getLogger(QuartzUtilityExample.class);

    /**
     * 编程式创建任务示例
     *
     * @param scheduler CoQuartzScheduler 实例（通过 Spring 注入）
     */
    public void createJobs(CoQuartzScheduler scheduler) {
        try {
            QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("sampleJob")
                .jobGroup("exampleGroup")
                .description("简单任务示例")
                .intervalInSeconds(30)
                .durable(true)
                .recoverable(false)
                .schedule(scheduler);

            QuartzTaskBuilder.newBuilder()
                .jobClass(CronJob.class)
                .jobName("cronJob")
                .description("Cron任务示例 - 每分钟执行")
                .cron("0 0/1 * * * ?")
                .schedule(scheduler);

            Map<String, Object> jobData = new HashMap<>();
            jobData.put("exampleParam", "exampleValue");

            com.coco.core.QuartzComponent quartzComponent = new com.coco.core.QuartzComponent.Builder()
                    .setTimeInterval(5)
                    .setTimeEnum(TimeEnum.MINUTES)
                    .setDescription("使用QuartzComponent的任务")
                    .setDurability(true)
                    .setShouldRecover(true)
                    .build();

            scheduler.scheduleSimpleIntervalJob(AdvancedJob.class,
                scheduler.getJobKey("advancedJob", "exampleGroup"),
                scheduler.getTriggerKey("advancedTrigger", "exampleGroup"),
                new org.quartz.JobDataMap(jobData),
                null,
                quartzComponent);

            logger.info("所有示例任务已成功调度");

        } catch (SchedulerException e) {
            logger.error("调度任务时发生错误", e);
        }
    }

    public static class SampleJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(SampleJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行简单任务: {}", context.getJobDetail().getKey());
            Thread.sleep(1000);
            logger.info("简单任务执行完成");
        }
    }

    public static class CronJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(CronJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行 Cron 任务: {}", context.getJobDetail().getKey());
            logger.info("Cron 任务执行完成 at {}", new java.util.Date());
        }
    }

    public static class AdvancedJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(AdvancedJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行高级任务: {}", context.getJobDetail().getKey());
            String param = context.getJobDetail().getJobDataMap().getString("exampleParam");
            logger.info("接收到参数: {}", param);
            Thread.sleep(2000);
            logger.info("高级任务执行完成");
        }
    }
}