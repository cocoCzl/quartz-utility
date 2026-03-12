package com.coco.example;

import com.coco.annotation.QuartzJob;
import com.coco.core.BaseAbstractQuartzJob;
import com.coco.core.CoQuartzScheduler;
import com.coco.core.QuartzTaskBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 新功能使用示例
 * 展示注解式任务定义、重试机制、超时控制等新功能
 */
public class NewFeaturesExample {

    /**
     * 示例1: 简单的注解任务
     * 每分钟执行一次
     */
    @Component
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
            // 业务逻辑
        }
    }

    /**
     * 示例2: Cron 表达式任务
     */
    @Component
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
            // 业务逻辑
        }
    }

    /**
     * 示例3: 带重试机制的任务
     * 失败后会重试 3 次，每次间隔 1 秒
     */
    @Component
    @QuartzJob(
        name = "retryJob",
        group = "retryJobs",
        description = "带重试机制的任务示例",
        cron = "0 0/5 * * * ?",  // 每 5 分钟执行一次
        retryTimes = 3,           // 失败后重试 3 次
        retryInterval = 1000      // 重试间隔 1 秒
    )
    public static class RetryJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(RetryJob.class);
        
        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行带重试机制的任务");
            
            // 模拟可能失败的操作
            if (Math.random() < 0.5) {
                throw new RuntimeException("模拟任务失败");
            }
            
            logger.info("任务执行成功");
        }
    }

    /**
     * 示例4: 带超时控制的任务
     * 任务最多执行 5 秒，超时后自动中断
     */
    @Component
    @QuartzJob(
        name = "timeoutJob",
        group = "timeoutJobs",
        description = "带超时控制的任务示例",
        cron = "0 0/10 * * * ?",  // 每 10 分钟执行一次
        timeout = 5000            // 超时时间 5 秒
    )
    public static class TimeoutJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(TimeoutJob.class);
        
        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行带超时控制的任务");
            
            // 模拟长时间运行的任务
            for (int i = 0; i < 20; i++) {
                logger.info("任务执行中... {}/20", i + 1);
                Thread.sleep(1000);
                
                // 检查是否被中断
                if (Thread.currentThread().isInterrupted()) {
                    logger.warn("任务被中断");
                    return;
                }
            }
            
            logger.info("任务执行完成");
        }
    }

    /**
     * 示例5: 综合示例
     * 同时使用重试和超时控制
     */
    @Component
    @QuartzJob(
        name = "comprehensiveJob",
        group = "comprehensiveJobs",
        description = "综合示例：重试 + 超时",
        cron = "0 0/15 * * * ?",   // 每 15 分钟执行一次
        retryTimes = 2,            // 失败后重试 2 次
        retryInterval = 2000,      // 重试间隔 2 秒
        timeout = 10000,           // 超时时间 10 秒
        durable = true,            // 任务持久化
        recoverable = true         // 任务可恢复
    )
    public static class ComprehensiveJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(ComprehensiveJob.class);
        
        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行综合任务（重试 + 超时）");
            
            // 获取当前重试次数
            int currentRetry = context.getJobDetail().getJobDataMap()
                    .getInt(BaseAbstractQuartzJob.CURRENT_RETRY_KEY);
            
            logger.info("当前执行次数: {}", currentRetry + 1);
            
            // 模拟业务逻辑
            Thread.sleep(3000);
            
            // 模拟随机失败
            if (Math.random() < 0.3 && currentRetry < 2) {
                throw new RuntimeException("模拟随机失败，将触发重试");
            }
            
            logger.info("综合任务执行成功");
        }
    }

    /**
     * ========================================
     * 功能 5: 使用 QuartzTaskBuilder 编程式创建任务
     * ========================================
     */
    
    @Component
    public static class ProgrammaticExample {
        private static final Logger logger = LoggerFactory.getLogger(ProgrammaticExample.class);
        
        @Autowired
        private CoQuartzScheduler scheduler;
        
        public void createJobWithBuilder() throws SchedulerException {
            // 使用 QuartzTaskBuilder 创建任务
            QuartzTaskBuilder.newBuilder()
                .jobClass(MyJob.class)
                .jobName("programmaticJob")
                .jobGroup("programmaticJobs")
                .description("编程式创建的任务")
                .cron("0 0/30 * * * ?")  // 每 30 分钟执行一次
                .retryTimes(2)            // 重试 2 次
                .retryInterval(3000L)     // 重试间隔 3 秒
                .timeout(15000L)          // 超时 15 秒
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
            // 业务逻辑
        }
    }
}
