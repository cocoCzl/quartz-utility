package com.coco.core;

import com.coco.annotation.QuartzJob;
import java.util.ArrayList;
import org.quartz.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quartz 任务注解处理器
 * 自动扫描并注册所有带有 @QuartzJob 注解的任务
 */
@Component
public class QuartzJobAnnotationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QuartzJobAnnotationProcessor.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CoQuartzScheduler scheduler;

    private final List<QuartzJobDefinition> jobDefinitions = new CopyOnWriteArrayList<>();

    /**
     * 应用启动完成后自动注册任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAnnotatedJobs() {
        logger.info("Starting to scan and register @QuartzJob annotated tasks");

        // 获取所有带有 @QuartzJob 注解的 Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(QuartzJob.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> beanClass = bean.getClass();

            // 检查是否实现了 Job 接口
            if (!Job.class.isAssignableFrom(beanClass)) {
                logger.warn("Bean {} is annotated with @QuartzJob but does not implement Job interface", 
                        entry.getKey());
                continue;
            }

            // 获取注解
            QuartzJob annotation = AnnotationUtils.findAnnotation(beanClass, QuartzJob.class);
            if (annotation == null) {
                continue;
            }

            // 检查是否启用
            if (!annotation.enabled()) {
                logger.info("Job {} is disabled, skipping registration", annotation.name());
                continue;
            }

            // 创建任务定义
            QuartzJobDefinition definition = QuartzJobDefinition.fromAnnotation(annotation, beanClass);
            jobDefinitions.add(definition);

            // 注册任务
            try {
                registerJob(definition);
                logger.info("Successfully registered job: name={}, group={}, class={}", 
                        definition.getName(), definition.getGroup(), beanClass.getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to register job: name={}, error={}", 
                        definition.getName(), e.getMessage(), e);
            }
        }

        logger.info("Completed scanning and registering @QuartzJob annotated tasks. Total: {}", jobDefinitions.size());
    }

    /**
     * 注册单个任务
     */
    private void registerJob(QuartzJobDefinition definition) throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Job> jobClass = (Class<? extends Job>) definition.getJobClass();

        QuartzTaskBuilder builder = QuartzTaskBuilder.newBuilder()
                .jobClass(jobClass)
                .jobName(definition.getName())
                .jobGroup(definition.getGroup())
                .description(definition.getDescription())
                .durable(definition.isDurable())
                .recoverable(definition.isRecoverable())
                .retryTimes(definition.getRetryTimes())           // 添加重试次数
                .retryInterval(definition.getRetryInterval())     // 添加重试间隔
                .timeout(definition.getTimeout());                // 添加超时时间

        // 设置触发器类型
        if (definition.isUseCron()) {
            builder.cron(definition.getCronExpression());
        } else if (definition.getIntervalSeconds() > 0) {
            builder.intervalInSeconds(definition.getIntervalSeconds());
        } else {
            logger.warn("Job {} has no trigger configuration (cron or interval), skipping", 
                    definition.getName());
            return;
        }

        // 设置 misfire 策略
        if (definition.getMisfireInstruction() != -1) {
            builder.misfireInstruction(definition.getMisfireInstruction());
        }

        // 调度任务
        builder.schedule(scheduler);
    }

    /**
     * 获取所有已注册的任务定义
     */
    public List<QuartzJobDefinition> getJobDefinitions() {
        return new ArrayList<>(jobDefinitions);
    }

    /**
     * 根据名称获取任务定义
     */
    public QuartzJobDefinition getJobDefinition(String name) {
        return jobDefinitions.stream()
                .filter(def -> def.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
