package com.coco.example;

import com.coco.core.BaseAbstractQuartzJob;
import com.coco.core.CoQuartzScheduler;
import com.coco.core.QuartzTaskBuilder;
import com.coco.enums.TimeEnum;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用示例：如何使用 Quartz Utility
 */
@Component
public class QuartzUtilityExample {
    
    private static final Logger logger = LoggerFactory.getLogger(QuartzUtilityExample.class);
    
    @Autowired
    private CoQuartzScheduler scheduler;
    
    @PostConstruct
    public void init() {
        try {
            // 示例1: 使用高级任务构建器创建简单任务
            QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("sampleJob")
                .jobGroup("exampleGroup")
                .description("简单任务示例")
                .intervalInSeconds(30) // 每30秒执行一次
                .durable(true)
                .recoverable(false)
                .schedule(scheduler);
                
            // 示例2: 使用 Cron 表达式创建任务
            QuartzTaskBuilder.newBuilder()
                .jobClass(CronJob.class)
                .jobName("cronJob")
                .description("Cron任务示例 - 每分钟执行")
                .cron("0 0/1 * * * ?")
                .schedule(scheduler);
                
            // 示例3: 使用 QuartzComponent 创建任务
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
    
    /**
     * 示例任务1: 简单任务
     */
    public static class SampleJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(SampleJob.class);
        
        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行简单任务: {}", context.getJobDetail().getKey());
            // 模拟任务逻辑
            Thread.sleep(1000); // 模拟执行时间
            logger.info("简单任务执行完成");
        }
    }
    
    /**
     * 示例任务2: Cron 任务
     */
    public static class CronJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(CronJob.class);
        
        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行 Cron 任务: {}", context.getJobDetail().getKey());
            // 业务逻辑
            logger.info("Cron 任务执行完成 at {}", new java.util.Date());
        }
    }
    
    /**
     * 示例任务3: 高级任务（使用参数）
     */
    public static class AdvancedJob extends BaseAbstractQuartzJob {
        private static final Logger logger = LoggerFactory.getLogger(AdvancedJob.class);
        
        @Override
        protected void executeQuartz(JobExecutionContext context) throws Throwable {
            logger.info("执行高级任务: {}", context.getJobDetail().getKey());
            
            // 获取传递的参数
            String param = context.getJobDetail().getJobDataMap().getString("exampleParam");
            logger.info("接收到参数: {}", param);
            
            // 模拟业务逻辑
            logger.info("高级任务业务逻辑执行中...");
            Thread.sleep(2000); // 模拟执行时间
            logger.info("高级任务执行完成");
        }
    }
}