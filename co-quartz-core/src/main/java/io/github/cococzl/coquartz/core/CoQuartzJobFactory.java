package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.DefaultLogSanitizer;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import org.quartz.*;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.util.concurrent.ExecutorService;

public class CoQuartzJobFactory extends SpringBeanJobFactory {

    private static final Logger log = LoggerFactory.getLogger(CoQuartzJobFactory.class);

    private AutowireCapableBeanFactory beanFactory;
    private SchedulerContext schedulerContext;
    private ObjectProvider<AsyncTaskLogService> asyncTaskLogServiceProvider;
    private ObjectProvider<TaskExecutionLogWriter> taskExecutionLogWriterProvider;
    private ExecutorService timeoutExecutor;
    private ObjectProvider<AlertEventPublisher> alertEventPublisherProvider;
    private CoQuartzProperties properties;
    private ObjectProvider<CoQuartzMetrics> metricsProvider;
    private ObjectProvider<LogSanitizer> logSanitizerProvider;
    private ObjectProvider<ReliableAuditService> reliableAuditServiceProvider;

    public CoQuartzJobFactory() {
    }

    public void setBeanFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public void setAsyncTaskLogServiceProvider(ObjectProvider<AsyncTaskLogService> provider) {
        this.asyncTaskLogServiceProvider = provider;
    }

    public void setTaskExecutionLogWriterProvider(ObjectProvider<TaskExecutionLogWriter> provider) {
        this.taskExecutionLogWriterProvider = provider;
    }

    public void setTimeoutExecutor(ExecutorService timeoutExecutor) {
        this.timeoutExecutor = timeoutExecutor;
    }

    public void setAlertEventPublisherProvider(ObjectProvider<AlertEventPublisher> provider) {
        this.alertEventPublisherProvider = provider;
    }

    public void setProperties(CoQuartzProperties properties) {
        this.properties = properties;
    }

    public void setMetricsProvider(ObjectProvider<CoQuartzMetrics> provider) {
        this.metricsProvider = provider;
    }

    public void setLogSanitizerProvider(ObjectProvider<LogSanitizer> provider) {
        this.logSanitizerProvider = provider;
    }

    public void setReliableAuditServiceProvider(ObjectProvider<ReliableAuditService> provider) {
        this.reliableAuditServiceProvider = provider;
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        JobDataMap jobDataMap = bundle.getJobDetail().getJobDataMap();
        Class<?> jobType = bundle.getJobDetail().getJobClass();
        String delegateName = jobDataMap.getString(CoQuartzConstants.DELEGATE_JOB_CLASS);
        if (delegateName != null && !delegateName.isBlank()) {
            jobType = Class.forName(delegateName, true, jobType.getClassLoader());
        }
        if (!Job.class.isAssignableFrom(jobType)) throw new SchedulerException("Delegate is not a Quartz Job");
        Object target;
        if (beanFactory != null) {
            target = beanFactory.autowire(jobType, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false);
            beanFactory.autowireBean(target);
        } else {
            var constructor = jobType.getDeclaredConstructor();
            constructor.setAccessible(true);
            target = constructor.newInstance();
        }
        var values = new org.springframework.beans.MutablePropertyValues();
        if (schedulerContext != null) values.addPropertyValues(schedulerContext);
        values.addPropertyValues(jobDataMap);
        values.addPropertyValues(bundle.getTrigger().getJobDataMap());
        org.springframework.beans.PropertyAccessorFactory.forBeanPropertyAccess(target).setPropertyValues(values, true);
        Object initialized = beanFactory == null ? target : beanFactory.initializeBean(target,
                jobType.getName() + "#quartz-" + java.util.UUID.randomUUID());
        ManagedJob job = new ManagedJob((Job) initialized, target, () -> {
            if (beanFactory != null) beanFactory.destroyBean(target);
        });

        TaskExecutionLogWriter logWriter = taskExecutionLogWriterProvider != null
                ? taskExecutionLogWriterProvider.getIfAvailable() : null;
        if (logWriter == null && asyncTaskLogServiceProvider != null) {
            logWriter = asyncTaskLogServiceProvider.getIfAvailable();
        }
        AlertEventPublisher alertEventPublisher = alertEventPublisherProvider != null ? alertEventPublisherProvider.getIfAvailable() : null;
        CoQuartzMetrics metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        LogSanitizer logSanitizer = logSanitizerProvider != null ? logSanitizerProvider.getIfAvailable() : new DefaultLogSanitizer();
        ReliableAuditService reliableAuditService = reliableAuditServiceProvider != null
                ? reliableAuditServiceProvider.getIfAvailable() : null;

        boolean enhanced = isEnhancedJob(jobDataMap);
        if (enhanced || (properties != null && properties.getLog().isReliableAudit())) {
            log.debug("Wrapping job {} with EnhancedJob", bundle.getJobDetail().getKey());
            EnhancedJob decorated = new EnhancedJob(job, enhanced ? jobDataMap : new JobDataMap(), logWriter,
                    timeoutExecutor, alertEventPublisher, properties, metrics, logSanitizer, reliableAuditService);
            decorated.setCleanup(job::close);
            return decorated;
        }

        return job;
    }

    @Override
    public void setSchedulerContext(SchedulerContext schedulerContext) {
        super.setSchedulerContext(schedulerContext);
        this.schedulerContext = schedulerContext;
    }

    private boolean isEnhancedJob(JobDataMap jobDataMap) {
        return jobDataMap.containsKey(CoQuartzConstants.ENHANCED)
                && jobDataMap.getBoolean(CoQuartzConstants.ENHANCED);
    }

}
