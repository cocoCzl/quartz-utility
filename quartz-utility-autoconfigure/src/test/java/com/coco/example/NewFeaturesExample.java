package com.coco.example;

import com.coco.annotation.QuartzJob;
import com.coco.core.BaseAbstractQuartzJob;
import com.coco.core.CoQuartzScheduler;
import com.coco.core.QuartzTaskBuilder;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 新功能使用示例（注解式）
 * 注意：此类不在生产 classpath 中，仅供参考。
 * 如需使用，请将相关内部类复制到你的项目中。
 */
public class NewFeaturesExample {

    @QuartzJob(
        name = "simpleAnnotationJob",
        group = "annotationJobs",
        description = "简单注解任务示例",
        intervalSeconds = 60
    )
    public static class SimpleAnnotationJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(SimpleAnnotationJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行简单注解任务: {}", context.getJobDetail().getKey());
        }
    }

    @QuartzJob(
        name = "cronAnnotationJob",
        group = "annotationJobs",
        description = "Cron注解任务 - 每小时执行",
        cron = "0 0 * * * ?"
    )
    public static class CronAnnotationJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(CronAnnotationJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行 Cron 注解任务: {}", context.getJobDetail().getKey());
        }
    }

    @QuartzJob(
        name = "retryJob",
        group = "retryJobs",
        description = "带重试机制的任务示例",
        cron = "0 0/5 * * * ?",
        retryTimes = 3,
        retryInterval = 1000
    )
    public static class RetryJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(RetryJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行带重试机制的任务");
            if (Math.random() < 0.5) {
                throw new RuntimeException("模拟任务失败");
            }
            logger.info("任务执行成功");
        }
    }

    @QuartzJob(
        name = "timeoutJob",
        group = "timeoutJobs",
        description = "带超时控制的任务示例",
        cron = "0 0/10 * * * ?",
        timeout = 5000
    )
    public static class TimeoutJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(TimeoutJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行带超时控制的任务");
            for (int i = 0; i < 20; i++) {
                logger.info("任务执行中... {}/20", i + 1);
                Thread.sleep(1000);
                if (Thread.currentThread().isInterrupted()) {
                    logger.warn("任务被中断");
                    return;
                }
            }
            logger.info("任务执行完成");
        }
    }

    @QuartzJob(
        name = "comprehensiveJob",
        group = "comprehensiveJobs",
        description = "综合示例：重试 + 超时",
        cron = "0 0/15 * * * ?",
        retryTimes = 2,
        retryInterval = 2000,
        timeout = 10000,
        durable = true,
        recoverable = true
    )
    public static class ComprehensiveJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(ComprehensiveJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行综合任务（重试 + 超时）");
            int currentRetry = context.getJobDetail().getJobDataMap()
                    .getInt(BaseAbstractQuartzJob.CURRENT_RETRY_KEY);
            logger.info("当前执行次数: {}", currentRetry + 1);
            Thread.sleep(3000);
            if (Math.random() < 0.3 && currentRetry < 2) {
                throw new RuntimeException("模拟随机失败，将触发重试");
            }
            logger.info("综合任务执行成功");
        }
    }

    public static class ProgrammaticExample {
        private static final Logger logger = LoggerFactory.getLogger(ProgrammaticExample.class);

        public void createJobWithBuilder(CoQuartzScheduler scheduler) throws Exception {
            QuartzTaskBuilder.newBuilder()
                .jobClass(MyJob.class)
                .jobName("programmaticJob")
                .jobGroup("programmaticJobs")
                .description("编程式创建的任务")
                .cron("0 0/30 * * * ?")
                .retryTimes(2)
                .retryInterval(3000L)
                .timeout(15000L)
                .durable(true)
                .recoverable(true)
                .schedule(scheduler);

            logger.info("编程式任务创建成功");
        }
    }

    public static class MyJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(MyJob.class);

        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行编程式任务");
        }
    }
}