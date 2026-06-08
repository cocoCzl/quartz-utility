package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.enums.MisfirePolicy;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class QuartzJobAnnotationProcessor {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobAnnotationProcessor.class);

    private final CoQuartzScheduler coQuartzScheduler;
    private final ApplicationContext applicationContext;
    private final CoQuartzProperties properties;

    private final List<QuartzJobDefinition> jobDefinitions = new CopyOnWriteArrayList<>();

    public QuartzJobAnnotationProcessor(CoQuartzScheduler coQuartzScheduler,
                                          ApplicationContext applicationContext,
                                          CoQuartzProperties properties) {
        this.coQuartzScheduler = coQuartzScheduler;
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerJobs() {
        if (!properties.getAnnotation().isEnabled()) {
            log.info("Co-Quartz @QuartzJob annotation scanning is disabled");
            return;
        }

        String[] beanNames = applicationContext.getBeanNamesForType(Job.class);
        int registered = 0;

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = AopUtils.getTargetClass(bean);

            QuartzJob annotation = beanClass.getAnnotation(QuartzJob.class);
            if (annotation == null) {
                continue;
            }

            if (!annotation.enabled()) {
                log.info("Skipping disabled @QuartzJob: {}", beanClass.getName());
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Job> jobClass = (Class<? extends Job>) beanClass;
            QuartzJobDefinition definition = QuartzJobDefinition.fromAnnotation(annotation, jobClass);

            try {
                scheduleJob(definition);
                jobDefinitions.add(definition);
                registered++;
                log.info("Registered @QuartzJob: {}:{}", definition.getGroup(), definition.getName());
            } catch (SchedulerException e) {
                log.error("Failed to register @QuartzJob: {}", beanClass.getName(), e);
            }
        }

        log.info("Co-Quartz registered {} @QuartzJob annotations", registered);
    }

    private void scheduleJob(QuartzJobDefinition definition) throws SchedulerException {
        String jobName = definition.getName();
        String jobGroup = definition.getGroup();

        if (jobName == null || jobName.isEmpty()) {
            jobName = definition.getJobClass().getSimpleName();
        }

        Map<String, Object> jobDataMap = definition.toJobDataMap();
        JobDataMap quartzJobDataMap = new JobDataMap(jobDataMap);

        JobDetail jobDetail = JobBuilder.newJob(definition.getJobClass())
                .withIdentity(jobName, jobGroup)
                .storeDurably(definition.isDurable())
                .requestRecovery(definition.isRecoverable())
                .usingJobData(quartzJobDataMap)
                .build();

        if (definition.getDescription() != null && !definition.getDescription().isEmpty()) {
            jobDetail = jobDetail.getJobBuilder().withDescription(definition.getDescription()).build();
        }

        Trigger trigger = buildTrigger(definition, jobName, jobGroup);
        Scheduler scheduler = coQuartzScheduler.getScheduler();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private Trigger buildTrigger(QuartzJobDefinition definition, String jobName, String jobGroup) {
        String triggerName = "TRIGGER_" + jobName;
        String triggerGroup = jobGroup;

        if (definition.getCronExpression() != null && !definition.getCronExpression().isEmpty()) {
            CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(definition.getCronExpression());
            applyCronMisfirePolicy(cronBuilder, definition.getMisfirePolicy());
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerName, triggerGroup)
                    .withSchedule(cronBuilder)
                    .startNow()
                    .build();
        } else if (definition.getIntervalSeconds() > 0) {
            SimpleScheduleBuilder simpleBuilder = SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(definition.getIntervalSeconds())
                    .repeatForever();
            applySimpleMisfirePolicy(simpleBuilder, definition.getMisfirePolicy());
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerName, triggerGroup)
                    .withSchedule(simpleBuilder)
                    .startNow()
                    .build();
        } else {
            throw new IllegalArgumentException("Either cronExpression or intervalSeconds must be specified for @QuartzJob: " + jobName);
        }
    }

    private void applyCronMisfirePolicy(CronScheduleBuilder builder, MisfirePolicy policy) {
        if (policy == null) return;
        switch (policy) {
            case FIRE_NOW -> builder.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> builder.withMisfireHandlingInstructionIgnoreMisfires();
        }
    }

    private void applySimpleMisfirePolicy(SimpleScheduleBuilder builder, MisfirePolicy policy) {
        if (policy == null) return;
        switch (policy) {
            case FIRE_NOW -> builder.withMisfireHandlingInstructionFireNow();
            case IGNORE_MISFIRES -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> builder.withMisfireHandlingInstructionNextWithExistingCount();
        }
    }

    public List<QuartzJobDefinition> getJobDefinitions() {
        return List.copyOf(jobDefinitions);
    }
}