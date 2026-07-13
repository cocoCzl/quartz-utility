package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import io.github.cococzl.coquartz.annotation.QuartzTask;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import io.github.cococzl.coquartz.exception.CoQuartzSchedulingException;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class QuartzJobAnnotationProcessor implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobAnnotationProcessor.class);
    private static final int CLUSTER_RECONCILIATION_ATTEMPTS = 3;

    private final CoQuartzScheduler coQuartzScheduler;
    private final ApplicationContext applicationContext;
    private final CoQuartzProperties properties;
    private final MethodTaskRegistry methodTaskRegistry;

    private final List<QuartzJobDefinition> jobDefinitions = new CopyOnWriteArrayList<>();

    public QuartzJobAnnotationProcessor(CoQuartzScheduler coQuartzScheduler,
                                          ApplicationContext applicationContext,
                                          CoQuartzProperties properties,
                                          MethodTaskRegistry methodTaskRegistry) {
        this.coQuartzScheduler = coQuartzScheduler;
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.methodTaskRegistry = methodTaskRegistry;
    }

    public QuartzJobAnnotationProcessor(CoQuartzScheduler coQuartzScheduler,
                                          ApplicationContext applicationContext,
                                          CoQuartzProperties properties) {
        this(coQuartzScheduler, applicationContext, properties,
                applicationContext.getBeanProvider(MethodTaskRegistry.class)
                        .getIfAvailable(() -> new MethodTaskRegistry(applicationContext)));
    }

    @Override
    public void afterSingletonsInstantiated() {
        registerJobs();
    }

    public void registerJobs() {
        if (!properties.getAnnotation().isEnabled()) {
            log.info("Co-Quartz @QuartzJob annotation scanning is disabled");
            return;
        }

        List<QuartzJobDefinition> discoveredDefinitions = new ArrayList<>();
        List<QuartzJobDefinition> disabledDefinitions = new ArrayList<>();
        discoverClassJobs(discoveredDefinitions, disabledDefinitions);
        discoverMethodTasks(discoveredDefinitions, disabledDefinitions);
        List<QuartzJobDefinition> allDefinitions = new ArrayList<>(discoveredDefinitions);
        allDefinitions.addAll(disabledDefinitions);
        validateUniqueIdentities(allDefinitions);

        List<PreparedSchedule> schedules = new ArrayList<>(discoveredDefinitions.size());
        for (QuartzJobDefinition definition : discoveredDefinitions) {
            schedules.add(prepareSchedule(definition));
        }

        try {
            for (PreparedSchedule schedule : schedules) {
                preflightSchedule(schedule);
            }
            for (QuartzJobDefinition definition : disabledDefinitions) {
                preflightDisabledDefinition(definition);
            }
            for (PreparedSchedule schedule : schedules) {
                reconcileScheduleWithRetry(schedule);
            }
            for (QuartzJobDefinition definition : disabledDefinitions) {
                reconcileDisabledDefinition(definition);
            }
        } catch (SchedulerException e) {
            throw new CoQuartzSchedulingException("Failed to reconcile Co-Quartz task definitions", e);
        }

        methodTaskRegistry.replaceDefinitions(discoveredDefinitions);
        jobDefinitions.clear();
        jobDefinitions.addAll(discoveredDefinitions);
        log.info("Co-Quartz reconciled {} task definitions", discoveredDefinitions.size());
    }

    /**
     * A JDBC JobStore makes each individual scheduler operation transactional, but the
     * read-then-create portion of reconciliation is necessarily optimistic.  When two
     * cluster nodes start together, one node can create the job between another node's
     * {@code getJobDetail} and {@code scheduleJob}.  Retrying the complete reconciliation
     * is safe and converges on the code-owned definition without treating that expected
     * registration race as an application-start failure.
     */
    private void reconcileScheduleWithRetry(PreparedSchedule desired) throws SchedulerException {
        for (int attempt = 1; attempt <= CLUSTER_RECONCILIATION_ATTEMPTS; attempt++) {
            try {
                reconcileSchedule(desired);
                return;
            } catch (ObjectAlreadyExistsException e) {
                if (attempt == CLUSTER_RECONCILIATION_ATTEMPTS) {
                    throw e;
                }
                log.debug("Concurrent Co-Quartz reconciliation detected for {}; retrying ({}/{})",
                        desired.jobDetail().getKey(), attempt, CLUSTER_RECONCILIATION_ATTEMPTS);
            }
        }
    }

    private void discoverClassJobs(List<QuartzJobDefinition> definitions,
                                   List<QuartzJobDefinition> disabledDefinitions) {
        String[] beanNames = applicationContext.getBeanNamesForType(Job.class);
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = AopUtils.getTargetClass(bean);
            QuartzJob annotation = AnnotatedElementUtils.findMergedAnnotation(beanClass, QuartzJob.class);
            if (annotation == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Job> jobClass = (Class<? extends Job>) beanClass;
            QuartzJobDefinition definition = QuartzJobDefinition.fromAnnotation(annotation, jobClass);
            if (annotation.enabled()) {
                definitions.add(definition);
            } else {
                disabledDefinitions.add(definition);
            }
        }
    }

    private void discoverMethodTasks(List<QuartzJobDefinition> definitions,
                                     List<QuartzJobDefinition> disabledDefinitions) {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            boolean declaredCandidate = beanType != null && !findQuartzTaskMethods(beanType).isEmpty();
            boolean initializedSingleton = applicationContext.getAutowireCapableBeanFactory()
                    instanceof ConfigurableListableBeanFactory beanFactory
                    && beanFactory.containsSingleton(beanName);
            if (!declaredCandidate && !initializedSingleton) {
                continue;
            }

            Object bean = applicationContext.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            Set<Method> taskMethods = findQuartzTaskMethods(targetClass);
            if (taskMethods.isEmpty()) {
                continue;
            }
            for (Method method : taskMethods) {
                QuartzTask annotation = AnnotatedElementUtils.findMergedAnnotation(method, QuartzTask.class);
                if (annotation == null) {
                    continue;
                }
                QuartzJobDefinition definition = QuartzJobDefinition.fromAnnotation(annotation, beanName, method);
                if (annotation.enabled()) {
                    validateMethodTask(beanName, bean, method, annotation);
                    definitions.add(definition);
                } else {
                    validateMethodTaskIdentity(beanName, method, annotation);
                    disabledDefinitions.add(definition);
                }
            }
        }
    }

    private Set<Method> findQuartzTaskMethods(Class<?> type) {
        return MethodIntrospector.selectMethods(type,
                (ReflectionUtils.MethodFilter) method ->
                        AnnotatedElementUtils.hasAnnotation(method, QuartzTask.class));
    }

    private void validateMethodTask(String beanName, Object bean, Method method, QuartzTask annotation) {
        String source = beanName + "#" + method.getName();
        if (!Modifier.isPublic(method.getModifiers())) {
            throw configurationError(source, "method must be public");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw configurationError(source, "method must be an instance method, not static");
        }
        if (method.getReturnType() != Void.TYPE) {
            throw configurationError(source, "method must return void");
        }
        if (method.getParameterCount() != 0) {
            throw configurationError(source, "method must declare no parameters");
        }
        validateMethodTaskIdentity(beanName, method, annotation);
        try {
            AopUtils.selectInvocableMethod(method, bean.getClass());
        } catch (IllegalStateException e) {
            throw new CoQuartzConfigurationException("Invalid @QuartzTask method " + source
                    + ": method is not invocable through the Spring bean", e);
        }
    }

    private void validateMethodTaskIdentity(String beanName, Method method, QuartzTask annotation) {
        String source = beanName + "#" + method.getName();
        if (annotation.name() == null || annotation.name().isBlank()) {
            throw configurationError(source, "task name must not be blank");
        }
        if (annotation.group() == null || annotation.group().isBlank()) {
            throw configurationError(source, "task group must not be blank");
        }
    }

    private CoQuartzConfigurationException configurationError(String source, String reason) {
        return new CoQuartzConfigurationException("Invalid @QuartzTask method " + source + ": " + reason);
    }

    private void validateUniqueIdentities(List<QuartzJobDefinition> definitions) {
        Map<JobKey, QuartzJobDefinition> byIdentity = new LinkedHashMap<>();
        for (QuartzJobDefinition definition : definitions) {
            String name = resolveName(definition);
            String group = resolveGroup(definition);
            JobKey key = JobKey.jobKey(name, group);
            QuartzJobDefinition previous = byIdentity.putIfAbsent(key, definition);
            if (previous != null) {
                throw new CoQuartzConfigurationException("Duplicate Co-Quartz task identity " + key
                        + " declared by " + previous.getSourceDescription()
                        + " and " + definition.getSourceDescription());
            }
        }
    }

    private String resolveName(QuartzJobDefinition definition) {
        String name = definition.getName();
        if (name == null || name.isBlank()) {
            name = definition.getJobClass().getSimpleName();
        }
        return name;
    }

    private String resolveGroup(QuartzJobDefinition definition) {
        String group = definition.getGroup();
        return group == null || group.isBlank() ? CoQuartzConstants.DEFAULT_GROUP : group;
    }

    private PreparedSchedule prepareSchedule(QuartzJobDefinition definition) {
        String jobName = resolveName(definition);
        String jobGroup = resolveGroup(definition);
        QuartzSchedule schedule;
        try {
            schedule = definition.resolveSchedule(properties.getScheduling().getDefaultTimeZone());
        } catch (CoQuartzConfigurationException e) {
            throw new CoQuartzConfigurationException("Invalid task definition "
                    + definition.getSourceDescription() + " (" + jobGroup + "." + jobName + "): "
                    + e.getMessage(), e);
        }

        Trigger trigger;
        try {
            trigger = QuartzTriggerFactory.build(
                    CoQuartzConstants.TRIGGER_KEY_PREFIX + jobName,
                    jobGroup,
                    schedule,
                    null,
                    null,
                    JobKey.jobKey(jobName, jobGroup));
        } catch (CoQuartzConfigurationException e) {
            throw new CoQuartzConfigurationException("Invalid task definition "
                    + definition.getSourceDescription() + " (" + jobGroup + "." + jobName + "): "
                    + e.getMessage(), e);
        }
        String jobFingerprint = QuartzDefinitionFingerprint.jobFingerprint(definition);
        String scheduleFingerprint = QuartzDefinitionFingerprint.scheduleFingerprint(
                schedule, trigger.getMisfireInstruction());
        String definitionVersion = QuartzDefinitionFingerprint.definitionVersion(
                jobFingerprint, scheduleFingerprint);

        Map<String, Object> jobDataMap = definition.toJobDataMap();
        JobDataMap quartzJobDataMap = new JobDataMap(jobDataMap);
        quartzJobDataMap.put(CoQuartzConstants.TIME_ZONE,
                schedule.timeZone() == null ? "" : schedule.timeZone());
        quartzJobDataMap.put(CoQuartzConstants.JOB_FINGERPRINT, jobFingerprint);
        quartzJobDataMap.put(CoQuartzConstants.SCHEDULE_FINGERPRINT, scheduleFingerprint);
        quartzJobDataMap.put(CoQuartzConstants.DEFINITION_VERSION, definitionVersion);
        if (!definition.isConcurrent()) {
            quartzJobDataMap.put(CoQuartzConstants.DELEGATE_JOB_CLASS, definition.getJobClass().getName());
        }

        JobDetail jobDetail = JobBuilder.newJob(definition.isConcurrent()
                        ? definition.getJobClass()
                        : NonConcurrentJobWrapper.class)
                .withIdentity(jobName, jobGroup)
                .storeDurably(definition.isDurable())
                .requestRecovery(definition.isRecoverable())
                .usingJobData(quartzJobDataMap)
                .build();

        if (definition.getDescription() != null && !definition.getDescription().isEmpty()) {
            jobDetail = jobDetail.getJobBuilder().withDescription(definition.getDescription()).build();
        }

        trigger.getJobDataMap().put(CoQuartzConstants.SCHEDULE_FINGERPRINT, scheduleFingerprint);
        return new PreparedSchedule(definition, jobDetail, trigger, jobFingerprint, scheduleFingerprint);
    }

    private void reconcileSchedule(PreparedSchedule desired) throws SchedulerException {
        Scheduler scheduler = coQuartzScheduler.getScheduler();
        JobKey jobKey = desired.jobDetail().getKey();
        TriggerKey triggerKey = desired.trigger().getKey();
        JobDetail existingJob = scheduler.getJobDetail(jobKey);

        if (existingJob == null) {
            Trigger unexpectedTrigger = scheduler.getTrigger(triggerKey);
            if (unexpectedTrigger != null) {
                throw new CoQuartzConfigurationException("Trigger identity " + triggerKey
                        + " already exists without the expected task " + jobKey);
            }
            scheduler.scheduleJob(desired.jobDetail(), desired.trigger());
            log.info("Created Co-Quartz task: {} from {}",
                    jobKey, desired.definition().getSourceDescription());
            return;
        }

        assertCodeOwned(existingJob, desired);
        Trigger existingTrigger = scheduler.getTrigger(triggerKey);
        if (existingTrigger != null && !jobKey.equals(existingTrigger.getJobKey())) {
            throw new CoQuartzConfigurationException("Trigger identity " + triggerKey
                    + " belongs to a different task: " + existingTrigger.getJobKey());
        }

        boolean pauseRestorePending = Boolean.parseBoolean(existingJob.getJobDataMap()
                .getString(CoQuartzConstants.PAUSE_RESTORE_PENDING));
        if (pauseRestorePending && existingTrigger != null) {
            scheduler.pauseTrigger(triggerKey);
        }

        String storedJobFingerprint = existingJob.getJobDataMap()
                .getString(CoQuartzConstants.JOB_FINGERPRINT);
        String storedScheduleFingerprint = existingTrigger == null ? null
                : existingTrigger.getJobDataMap().getString(CoQuartzConstants.SCHEDULE_FINGERPRINT);
        String actualJobFingerprint = QuartzDefinitionFingerprint.jobFingerprint(existingJob);
        String actualScheduleFingerprint = existingTrigger == null ? null
                : QuartzDefinitionFingerprint.scheduleFingerprint(existingTrigger);
        boolean jobChanged = !desired.jobFingerprint().equals(storedJobFingerprint)
                || !desired.jobFingerprint().equals(actualJobFingerprint);
        boolean scheduleChanged = existingTrigger == null
                || !desired.scheduleFingerprint().equals(storedScheduleFingerprint)
                || !desired.scheduleFingerprint().equals(actualScheduleFingerprint);

        if (!jobChanged && !scheduleChanged) {
            if (pauseRestorePending) {
                scheduler.addJob(desired.jobDetail(), true, true);
            }
            log.debug("Co-Quartz task definition unchanged: {}", jobKey);
            return;
        }

        boolean mustRestorePause = pauseRestorePending
                || (scheduleChanged && existingTrigger != null
                && scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.PAUSED);
        JobDetail replacement = scheduleChanged && mustRestorePause
                ? withPauseRestorePending(desired.jobDetail())
                : desired.jobDetail();
        scheduler.addJob(replacement, true, true);
        if (scheduleChanged) {
            if (existingTrigger == null) {
                scheduler.scheduleJob(desired.trigger());
            } else {
                if (scheduler.rescheduleJob(triggerKey, desired.trigger()) == null) {
                    throw new SchedulerException("Trigger disappeared while rescheduling: " + triggerKey);
                }
            }
            if (mustRestorePause) {
                scheduler.pauseTrigger(triggerKey);
                scheduler.addJob(desired.jobDetail(), true, true);
            }
        }

        log.info("Updated Co-Quartz task: {} (jobChanged={}, scheduleChanged={}) from {}",
                jobKey, jobChanged, scheduleChanged, desired.definition().getSourceDescription());
    }

    private JobDetail withPauseRestorePending(JobDetail jobDetail) {
        JobDataMap dataMap = new JobDataMap(jobDetail.getJobDataMap());
        dataMap.put(CoQuartzConstants.PAUSE_RESTORE_PENDING, "true");
        return jobDetail.getJobBuilder().usingJobData(dataMap).build();
    }

    private void preflightSchedule(PreparedSchedule desired) throws SchedulerException {
        Scheduler scheduler = coQuartzScheduler.getScheduler();
        JobKey jobKey = desired.jobDetail().getKey();
        TriggerKey triggerKey = desired.trigger().getKey();
        JobDetail existingJob = scheduler.getJobDetail(jobKey);
        Trigger existingTrigger = scheduler.getTrigger(triggerKey);
        if (existingJob == null) {
            if (existingTrigger != null) {
                throw new CoQuartzConfigurationException("Trigger identity " + triggerKey
                        + " already exists without the expected task " + jobKey);
            }
            return;
        }
        assertCodeOwned(existingJob, desired);
        if (existingTrigger != null && !jobKey.equals(existingTrigger.getJobKey())) {
            throw new CoQuartzConfigurationException("Trigger identity " + triggerKey
                    + " belongs to a different task: " + existingTrigger.getJobKey());
        }
        List<? extends Trigger> jobTriggers = scheduler.getTriggersOfJob(jobKey);
        List<TriggerKey> unexpectedTriggerKeys = jobTriggers.stream()
                .map(Trigger::getKey)
                .filter(key -> !triggerKey.equals(key))
                .toList();
        if (!unexpectedTriggerKeys.isEmpty()) {
            throw new CoQuartzConfigurationException("Code-owned task " + jobKey
                    + " has unexpected additional triggers " + unexpectedTriggerKeys
                    + "; refusing to overwrite an ambiguous schedule");
        }
    }

    private void preflightDisabledDefinition(QuartzJobDefinition definition) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(resolveName(definition), resolveGroup(definition));
        JobDetail existingJob = coQuartzScheduler.getScheduler().getJobDetail(jobKey);
        if (existingJob != null) {
            assertCodeOwned(existingJob, definition.getSourceDescription());
        }
    }

    private void reconcileDisabledDefinition(QuartzJobDefinition definition) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(resolveName(definition), resolveGroup(definition));
        Scheduler scheduler = coQuartzScheduler.getScheduler();
        if (scheduler.getJobDetail(jobKey) != null) {
            scheduler.deleteJob(jobKey);
            log.info("Removed disabled code-owned Co-Quartz task: {} from {}",
                    jobKey, definition.getSourceDescription());
        }
    }

    private void assertCodeOwned(JobDetail existingJob, PreparedSchedule desired) {
        assertCodeOwned(existingJob, desired.definition().getSourceDescription());
    }

    private void assertCodeOwned(JobDetail existingJob, String requestedBy) {
        JobDataMap dataMap = existingJob.getJobDataMap();
        boolean owned = CoQuartzConstants.OWNER_VALUE.equals(dataMap.getString(CoQuartzConstants.OWNER));
        boolean codeOwned = Boolean.parseBoolean(dataMap.getString(CoQuartzConstants.CODE_OWNED));
        if (!owned || !codeOwned) {
            throw new CoQuartzConfigurationException("Task identity " + existingJob.getKey()
                    + " is already owned by a non-code Co-Quartz or external Quartz task; refusing to overwrite it. "
                    + "Requested by " + requestedBy);
        }
        String metadataVersion = dataMap.getString(CoQuartzConstants.METADATA_VERSION);
        if (metadataVersion != null && !metadataVersion.isBlank()
                && !CoQuartzConstants.METADATA_VERSION_VALUE.equals(metadataVersion)) {
            throw new CoQuartzConfigurationException("Task identity " + existingJob.getKey()
                    + " uses unsupported Co-Quartz metadata version " + metadataVersion
                    + "; refusing to downgrade or overwrite it");
        }
    }

    public List<QuartzJobDefinition> getJobDefinitions() {
        return List.copyOf(jobDefinitions);
    }

    private record PreparedSchedule(QuartzJobDefinition definition,
                                    JobDetail jobDetail,
                                    Trigger trigger,
                                    String jobFingerprint,
                                    String scheduleFingerprint) {
    }
}
