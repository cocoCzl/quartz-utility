package io.github.cococzl.coquartz.config;

import io.github.cococzl.coquartz.core.CoQuartzJobFactory;
import io.github.cococzl.coquartz.core.CoQuartzScheduler;
import io.github.cococzl.coquartz.core.QuartzJobAnnotationProcessor;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.event.DefaultAlertEventListener;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskAdminService;
import io.github.cococzl.coquartz.service.TaskQueryService;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration
@AutoConfigureAfter(org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration.class)
@EnableConfigurationProperties(CoQuartzProperties.class)
@ConditionalOnClass(Scheduler.class)
@ConditionalOnBean(Scheduler.class)
public class CoQuartzCoreAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService coQuartzTimeoutExecutor(CoQuartzProperties properties) {
        CoQuartzProperties.TimeoutPoolConfig poolConfig = properties.getTimeoutPool();
        return new ScheduledThreadPoolExecutor(poolConfig.getCoreSize(), new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "co-quartz-timeout-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Bean
    public CoQuartzJobFactory coQuartzJobFactory(AutowireCapableBeanFactory beanFactory,
                                                   ObjectProvider<AsyncTaskLogService> asyncTaskLogServiceProvider,
                                                   ScheduledExecutorService coQuartzTimeoutExecutor,
                                                   ObjectProvider<AlertEventPublisher> alertEventPublisherProvider,
                                                   CoQuartzProperties properties,
                                                   ObjectProvider<CoQuartzMetrics> metricsProvider) {
        CoQuartzJobFactory jobFactory = new CoQuartzJobFactory();
        jobFactory.setBeanFactory(beanFactory);
        jobFactory.setAsyncTaskLogServiceProvider(asyncTaskLogServiceProvider);
        jobFactory.setTimeoutExecutor(coQuartzTimeoutExecutor);
        jobFactory.setAlertEventPublisherProvider(alertEventPublisherProvider);
        jobFactory.setProperties(properties);
        jobFactory.setMetricsProvider(metricsProvider);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBeanCustomizer coQuartzJobFactoryCustomizer(CoQuartzJobFactory coQuartzJobFactory) {
        return factory -> factory.setJobFactory(coQuartzJobFactory);
    }

    @Bean
    public CoQuartzScheduler coQuartzScheduler(Scheduler scheduler) {
        return new CoQuartzScheduler(scheduler);
    }

    @Bean
    public TaskAdminService taskAdminService(Scheduler scheduler) {
        return new TaskAdminService(scheduler);
    }

    @Bean
    public TaskQueryService taskQueryService(Scheduler scheduler) {
        return new TaskQueryService(scheduler);
    }

    @Bean
    public QuartzJobAnnotationProcessor quartzJobAnnotationProcessor(
            CoQuartzScheduler coQuartzScheduler,
            ApplicationContext applicationContext,
            CoQuartzProperties properties) {
        return new QuartzJobAnnotationProcessor(coQuartzScheduler, applicationContext, properties);
    }

    @Bean
    public DefaultAlertEventListener defaultAlertEventListener() {
        return new DefaultAlertEventListener();
    }
}