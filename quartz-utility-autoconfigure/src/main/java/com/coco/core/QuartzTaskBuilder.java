package com.coco.core;

import org.quartz.*;

import java.util.Map;

/**
 * 高级任务构建器，提供更简单的API来创建和调度任务
 */
public class QuartzTaskBuilder {
    
    private Class<? extends Job> jobClass;
    private String jobName;
    private String jobGroup = "DEFAULT";
    private String triggerName;
    private String triggerGroup = "DEFAULT";
    private String description;
    private Map<String, Object> jobDataMap;
    private String cronExpression;
    private int intervalInSeconds = 60; // 默认60秒
    private boolean useCron = false;
    private boolean shouldRecover = false;
    private boolean durability = true;
    private int misfireInstruction = -1;
    
    private QuartzTaskBuilder() {}
    
    public static QuartzTaskBuilder newBuilder() {
        return new QuartzTaskBuilder();
    }
    
    public QuartzTaskBuilder jobClass(Class<? extends Job> jobClass) {
        this.jobClass = jobClass;
        return this;
    }
    
    public QuartzTaskBuilder jobName(String jobName) {
        this.jobName = jobName;
        return this;
    }
    
    public QuartzTaskBuilder jobGroup(String jobGroup) {
        this.jobGroup = jobGroup;
        return this;
    }
    
    public QuartzTaskBuilder triggerName(String triggerName) {
        this.triggerName = triggerName;
        return this;
    }
    
    public QuartzTaskBuilder triggerGroup(String triggerGroup) {
        this.triggerGroup = triggerGroup;
        return this;
    }
    
    public QuartzTaskBuilder description(String description) {
        this.description = description;
        return this;
    }
    
    public QuartzTaskBuilder jobData(Map<String, Object> jobDataMap) {
        this.jobDataMap = jobDataMap;
        return this;
    }
    
    public QuartzTaskBuilder cron(String cronExpression) {
        this.cronExpression = cronExpression;
        this.useCron = true;
        return this;
    }
    
    public QuartzTaskBuilder intervalInSeconds(int intervalInSeconds) {
        this.intervalInSeconds = intervalInSeconds;
        this.useCron = false;
        return this;
    }
    
    public QuartzTaskBuilder recoverable(boolean shouldRecover) {
        this.shouldRecover = shouldRecover;
        return this;
    }
    
    public QuartzTaskBuilder durable(boolean durability) {
        this.durability = durability;
        return this;
    }
    
    
    
    public QuartzTaskBuilder misfireInstruction(int misfireInstruction) {
        this.misfireInstruction = misfireInstruction;
        return this;
    }
    
    /**
     * 构建任务调度配置
     */
    public QuartzComponent buildQuartzComponent() {
        QuartzComponent.Builder builder = new QuartzComponent.Builder()
                .setDescription(description != null ? description : "Task: " + jobName)
                .setShouldRecover(shouldRecover)
                .setDurability(durability);
        
        if (useCron && cronExpression != null) {
            builder.setCronExpression(cronExpression);
        } else {
            builder.setTimeInterval(intervalInSeconds);
            // 根据时间间隔选择合适的时间单位
            if (intervalInSeconds >= 3600) {
                builder.setTimeEnum(com.coco.enums.TimeEnum.HOURS);
                builder.setTimeInterval(intervalInSeconds / 3600);
            } else if (intervalInSeconds >= 60) {
                builder.setTimeEnum(com.coco.enums.TimeEnum.MINUTES);
                builder.setTimeInterval(intervalInSeconds / 60);
            } else {
                builder.setTimeEnum(com.coco.enums.TimeEnum.SECONDS);
                builder.setTimeInterval(intervalInSeconds);
            }
        }
        
        if (misfireInstruction != -1) {
            builder.setMisfireInstruction(misfireInstruction);
        }
        
        return builder.build();
    }
    
    /**
     * 调度任务
     */
    public void schedule(CoQuartzScheduler scheduler) throws SchedulerException {
        JobKey jobKey = scheduler.getJobKey(jobName, jobGroup);
        TriggerKey triggerKey = scheduler.getTriggerKey(triggerName != null ? triggerName : jobName, triggerGroup);
        
        JobDataMap dataMap = null;
        if (jobDataMap != null) {
            dataMap = new JobDataMap(jobDataMap);
        }
        
        QuartzComponent component = buildQuartzComponent();
        
        if (useCron && cronExpression != null) {
            scheduler.scheduleCronJob(jobClass, jobKey, triggerKey, dataMap, null, component);
        } else {
            scheduler.scheduleSimpleIntervalJob(jobClass, jobKey, triggerKey, dataMap, null, component);
        }
    }
    
    /**
     * 立即执行一次任务
     */
    public void executeNow(CoQuartzScheduler scheduler) throws SchedulerException {
        JobKey jobKey = scheduler.getJobKey(jobName, jobGroup);
        scheduler.scheduler().triggerJob(jobKey);
    }
}