package io.github.cococzl.coquartz.config;

import io.github.cococzl.coquartz.core.CoQuartzJobFactory;
import io.github.cococzl.coquartz.core.CoQuartzScheduler;
import io.github.cococzl.coquartz.core.CoQuartzTimeoutExecutor;
import io.github.cococzl.coquartz.core.MethodTaskRegistry;
import io.github.cococzl.coquartz.core.QuartzJobAnnotationProcessor;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.event.DefaultAlertEventListener;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.DefaultLogSanitizer;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskAdminService;
import io.github.cococzl.coquartz.service.TaskQueryService;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.github.cococzl.coquartz.listener.CoQuartzJobListener;
import org.quartz.SchedulerException;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration
@AutoConfigureAfter(org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration.class)
@EnableConfigurationProperties(CoQuartzProperties.class)
@ConditionalOnClass(Scheduler.class)
@ConditionalOnBean(Scheduler.class)
public class CoQuartzCoreAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "coQuartzTimeoutExecutor")
    public ExecutorService coQuartzTimeoutExecutor(CoQuartzProperties properties) {
        CoQuartzProperties.TimeoutPoolConfig poolConfig = properties.getTimeoutPool();
        return new CoQuartzTimeoutExecutor(poolConfig.getCoreSize(), poolConfig.getMaxSize(),
                poolConfig.getShutdownAwaitMs(), new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "co-quartz-timeout-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "coQuartzAlertExecutor")
    public ExecutorService coQuartzAlertExecutor() {
        return new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(256), r -> {
            Thread thread = new Thread(r, "co-quartz-alert");
            thread.setDaemon(true);
            return thread;
        }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @ConditionalOnMissingBean(CoQuartzJobFactory.class)
    public CoQuartzJobFactory coQuartzJobFactory(AutowireCapableBeanFactory beanFactory,
                                                   ObjectProvider<AsyncTaskLogService> asyncTaskLogServiceProvider,
                                                   ObjectProvider<TaskExecutionLogWriter> logWriterProvider,
                                                   @Qualifier("coQuartzTimeoutExecutor") ExecutorService coQuartzTimeoutExecutor,
                                                   ObjectProvider<AlertEventPublisher> alertEventPublisherProvider,
                                                   CoQuartzProperties properties,
                                                   ObjectProvider<CoQuartzMetrics> metricsProvider,
                                                   ObjectProvider<LogSanitizer> logSanitizerProvider,
                                                   ObjectProvider<ReliableAuditService> reliableAuditServiceProvider) {
        CoQuartzJobFactory jobFactory = new CoQuartzJobFactory();
        jobFactory.setBeanFactory(beanFactory);
        jobFactory.setAsyncTaskLogServiceProvider(asyncTaskLogServiceProvider);
        jobFactory.setTaskExecutionLogWriterProvider(logWriterProvider);
        jobFactory.setTimeoutExecutor(coQuartzTimeoutExecutor);
        jobFactory.setAlertEventPublisherProvider(alertEventPublisherProvider);
        jobFactory.setProperties(properties);
        jobFactory.setMetricsProvider(metricsProvider);
        jobFactory.setLogSanitizerProvider(logSanitizerProvider);
        jobFactory.setReliableAuditServiceProvider(reliableAuditServiceProvider);
        return jobFactory;
    }

    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton reliableAuditValidator(
            CoQuartzProperties properties, ObjectProvider<ReliableAuditService> audit, org.springframework.core.env.Environment environment) {
        return () -> {
            if (Boolean.parseBoolean(environment.getProperty("spring.quartz.properties.org.quartz.jobStore.useProperties", "false"))) {
                throw new io.github.cococzl.coquartz.exception.CoQuartzConfigurationException(
                        "Quartz useProperties=true is unsupported; Co-Quartz requires typed JobDataMap values");
            }
            if (properties.getLog().isReliableAudit() && audit.getIfAvailable() == null) {
                throw new io.github.cococzl.coquartz.exception.CoQuartzConfigurationException(
                        "reliable-audit requires an available ReliableAuditService");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(LogSanitizer.class)
    public LogSanitizer coQuartzLogSanitizer() {
        return new DefaultLogSanitizer();
    }

    @Bean
    @ConditionalOnMissingBean(name = "coQuartzJobFactoryCustomizer")
    public SchedulerFactoryBeanCustomizer coQuartzJobFactoryCustomizer(CoQuartzJobFactory coQuartzJobFactory,
                                                                               org.springframework.core.env.Environment environment) {
        if (Boolean.parseBoolean(environment.getProperty("spring.quartz.properties.org.quartz.jobStore.useProperties", "false"))) {
            throw new io.github.cococzl.coquartz.exception.CoQuartzConfigurationException(
                    "Quartz useProperties=true is unsupported; Co-Quartz requires typed JobDataMap values");
        }
        return factory -> factory.setJobFactory(coQuartzJobFactory);
    }

    @Bean
    @ConditionalOnMissingBean(CoQuartzScheduler.class)
    public CoQuartzScheduler coQuartzScheduler(Scheduler scheduler, CoQuartzProperties properties) {
        return new CoQuartzScheduler(scheduler, properties);
    }

    @Bean
    @ConditionalOnMissingBean(TaskAdminService.class)
    public TaskAdminService taskAdminService(Scheduler scheduler, CoQuartzProperties properties) {
        return new TaskAdminService(scheduler, properties);
    }

    @Bean
    @ConditionalOnMissingBean(TaskQueryService.class)
    public TaskQueryService taskQueryService(Scheduler scheduler) {
        return new TaskQueryService(scheduler);
    }

    @Bean
    @ConditionalOnMissingBean(MethodTaskRegistry.class)
    public MethodTaskRegistry methodTaskRegistry(ApplicationContext applicationContext) {
        return new MethodTaskRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(QuartzJobAnnotationProcessor.class)
    public QuartzJobAnnotationProcessor quartzJobAnnotationProcessor(
            CoQuartzScheduler coQuartzScheduler,
            ApplicationContext applicationContext,
            CoQuartzProperties properties,
            MethodTaskRegistry methodTaskRegistry) {
        return new QuartzJobAnnotationProcessor(coQuartzScheduler, applicationContext, properties, methodTaskRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(DefaultAlertEventListener.class)
    public DefaultAlertEventListener defaultAlertEventListener() {
        return new DefaultAlertEventListener();
    }

    @Bean
    @ConditionalOnMissingBean(AlertEventPublisher.class)
    public AlertEventPublisher alertEventPublisher(ApplicationEventPublisher eventPublisher,
                                                     CoQuartzProperties properties,
                                                     @Qualifier("coQuartzAlertExecutor") java.util.concurrent.Executor alertExecutor,
                                                     LogSanitizer sanitizer) {
        AlertEventPublisher publisher = new AlertEventPublisher(eventPublisher, properties, alertExecutor);
        publisher.setLogSanitizer(sanitizer);
        return publisher;
    }

    @Bean
    @ConditionalOnMissingBean(CoQuartzJobListener.class)
    public CoQuartzJobListener coQuartzJobListener(Scheduler scheduler,
                                                    ObjectProvider<TaskExecutionLogWriter> logWriterProvider,
                                                    ObjectProvider<CoQuartzMetrics> metricsProvider,
                                                    ObjectProvider<AlertEventPublisher> alertPublisherProvider,
                                                    CoQuartzProperties properties,
                                                    LogSanitizer logSanitizer,
                                                    ObjectProvider<ReliableAuditService> auditProvider) throws SchedulerException {
        CoQuartzJobListener listener = new CoQuartzJobListener(logWriterProvider, metricsProvider,
                alertPublisherProvider, properties, logSanitizer, auditProvider);
        scheduler.getListenerManager().addJobListener(listener);
        return listener;
    }
}
