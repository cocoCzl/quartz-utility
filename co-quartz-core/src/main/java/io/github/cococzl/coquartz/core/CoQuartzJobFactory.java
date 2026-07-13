package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.DefaultLogSanitizer;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
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
    private ObjectProvider<AsyncTaskLogService> asyncTaskLogServiceProvider;
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
        Object job = createDelegateJobInstance(bundle, jobDataMap);

        if (beanFactory != null) {
            beanFactory.autowireBean(job);
        }

        AsyncTaskLogService asyncTaskLogService = asyncTaskLogServiceProvider != null ? asyncTaskLogServiceProvider.getIfAvailable() : null;
        AlertEventPublisher alertEventPublisher = alertEventPublisherProvider != null ? alertEventPublisherProvider.getIfAvailable() : null;
        CoQuartzMetrics metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        LogSanitizer logSanitizer = logSanitizerProvider != null ? logSanitizerProvider.getIfAvailable() : new DefaultLogSanitizer();
        ReliableAuditService reliableAuditService = reliableAuditServiceProvider != null
                ? reliableAuditServiceProvider.getIfAvailable() : null;

        if (isEnhancedJob(jobDataMap) && asyncTaskLogService != null && timeoutExecutor != null) {
            log.debug("Wrapping job {} with EnhancedJob", bundle.getJobDetail().getKey());
            return new EnhancedJob((Job) job, jobDataMap, asyncTaskLogService, timeoutExecutor, alertEventPublisher,
                    properties, metrics, logSanitizer, reliableAuditService);
        }

        return job;
    }

    private Object createDelegateJobInstance(TriggerFiredBundle bundle, JobDataMap jobDataMap) throws Exception {
        String delegateClassName = jobDataMap.getString(CoQuartzConstants.DELEGATE_JOB_CLASS);
        if (delegateClassName == null || delegateClassName.isBlank()) {
            return super.createJobInstance(bundle);
        }
        ClassLoader classLoader = bundle.getJobDetail().getJobClass().getClassLoader();
        Class<?> delegateClass = Class.forName(delegateClassName, true, classLoader);
        if (!Job.class.isAssignableFrom(delegateClass)) {
            throw new SchedulerException("Configured non-concurrent delegate is not a Quartz Job: " + delegateClassName);
        }
        java.lang.reflect.Constructor<?> constructor = delegateClass.getDeclaredConstructor();
        if (!constructor.canAccess(null)) {
            constructor.setAccessible(true);
        }
        return constructor.newInstance();
    }

    private boolean isEnhancedJob(JobDataMap jobDataMap) {
        return jobDataMap.containsKey(CoQuartzConstants.ENHANCED)
                && jobDataMap.getBoolean(CoQuartzConstants.ENHANCED);
    }

}
