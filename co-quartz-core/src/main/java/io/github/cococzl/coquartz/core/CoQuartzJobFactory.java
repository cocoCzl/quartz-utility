package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import org.quartz.*;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.util.concurrent.ScheduledExecutorService;

public class CoQuartzJobFactory extends SpringBeanJobFactory {

    private static final Logger log = LoggerFactory.getLogger(CoQuartzJobFactory.class);

    private AutowireCapableBeanFactory beanFactory;
    private ObjectProvider<AsyncTaskLogService> asyncTaskLogServiceProvider;
    private ScheduledExecutorService timeoutExecutor;
    private ObjectProvider<AlertEventPublisher> alertEventPublisherProvider;
    private CoQuartzProperties properties;
    private ObjectProvider<CoQuartzMetrics> metricsProvider;

    public CoQuartzJobFactory() {
    }

    public void setBeanFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public void setAsyncTaskLogServiceProvider(ObjectProvider<AsyncTaskLogService> provider) {
        this.asyncTaskLogServiceProvider = provider;
    }

    public void setTimeoutExecutor(ScheduledExecutorService timeoutExecutor) {
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

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);

        if (beanFactory != null) {
            beanFactory.autowireBean(job);
        }

        AsyncTaskLogService asyncTaskLogService = asyncTaskLogServiceProvider != null ? asyncTaskLogServiceProvider.getIfAvailable() : null;
        AlertEventPublisher alertEventPublisher = alertEventPublisherProvider != null ? alertEventPublisherProvider.getIfAvailable() : null;
        CoQuartzMetrics metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;

        JobDataMap jobDataMap = bundle.getJobDetail().getJobDataMap();
        if (isEnhancedJob(jobDataMap) && asyncTaskLogService != null && timeoutExecutor != null) {
            log.debug("Wrapping job {} with EnhancedJob", bundle.getJobDetail().getKey());
            EnhancedJob enhancedJob = new EnhancedJob((Job) job, jobDataMap, asyncTaskLogService, timeoutExecutor, alertEventPublisher, properties, metrics);
            if (isNonConcurrent(jobDataMap)) {
                return new NonConcurrentJobWrapper(enhancedJob);
            }
            return enhancedJob;
        }

        return job;
    }

    private boolean isEnhancedJob(JobDataMap jobDataMap) {
        return jobDataMap.containsKey(CoQuartzConstants.ENHANCED)
                && jobDataMap.getBoolean(CoQuartzConstants.ENHANCED);
    }

    private boolean isNonConcurrent(JobDataMap jobDataMap) {
        if (!jobDataMap.containsKey(CoQuartzConstants.CONCURRENT)) {
            return true;
        }
        Object val = jobDataMap.get(CoQuartzConstants.CONCURRENT);
        if (val instanceof Boolean) return !(Boolean) val;
        if (val instanceof String) return !"true".equalsIgnoreCase((String) val);
        return true;
    }
}