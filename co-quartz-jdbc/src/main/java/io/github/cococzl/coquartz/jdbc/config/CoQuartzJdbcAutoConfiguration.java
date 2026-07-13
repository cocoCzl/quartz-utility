package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.jdbc.job.LogCleanupRegistrar;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.jdbc.service.JdbcAsyncTaskLogService;
import io.github.cococzl.coquartz.jdbc.service.JdbcTaskMonitoringService;
import io.github.cococzl.coquartz.jdbc.service.JdbcReliableAuditService;
import io.github.cococzl.coquartz.jdbc.service.TaskLogService;
import io.github.cococzl.coquartz.listener.CoQuartzJobListener;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import io.github.cococzl.coquartz.service.TaskMonitoringService;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(io.github.cococzl.coquartz.config.CoQuartzCoreAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "co-quartz.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoQuartzProperties.class)
public class CoQuartzJdbcAutoConfiguration {

    @Bean(name = "coQuartzLogDataSource")
    @ConditionalOnMissingBean(name = "coQuartzLogDataSource")
    public DataSource coQuartzLogDataSource(DataSource applicationDataSource, CoQuartzProperties properties) {
        CoQuartzProperties.LogConfig.DataSourceConfig config = properties.getLog().getDatasource();
        if (config.getUrl() == null || config.getUrl().isBlank()) return applicationDataSource;
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
            dataSource.setDriverClassName(config.getDriverClassName());
        }
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean(name = "coQuartzJdbcTemplate")
    public JdbcTemplate coQuartzJdbcTemplate(@Qualifier("coQuartzLogDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(TaskLogRepository.class)
    public JdbcTaskLogRepository jdbcTaskLogRepository(JdbcTemplate coQuartzJdbcTemplate) {
        return new JdbcTaskLogRepository(coQuartzJdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AsyncTaskLogService.class)
    public JdbcAsyncTaskLogService jdbcAsyncTaskLogService(TaskLogRepository taskLogRepository,
                                                             CoQuartzProperties properties,
                                                             ApplicationEventPublisher eventPublisher,
                                                             ObjectProvider<AlertEventPublisher> alertEventPublisherProvider) {
        CoQuartzProperties.AsyncConfig asyncConfig = properties.getAsync();
        return new JdbcAsyncTaskLogService(taskLogRepository,
                asyncConfig.getLogQueueCapacity(),
                asyncConfig.getLogBatchSize(),
                asyncConfig.getLogFlushIntervalMs(),
                asyncConfig.getShutdownFlushTimeoutMs(),
                asyncConfig.getLogWriteMaxRetries(), eventPublisher, alertEventPublisherProvider);
    }

    @Bean
    @ConditionalOnMissingBean(TaskMonitoringService.class)
    public JdbcTaskMonitoringService jdbcTaskMonitoringService(TaskLogRepository taskLogRepository) {
        return new JdbcTaskMonitoringService(taskLogRepository);
    }

    @Bean
    @ConditionalOnBean({AsyncTaskLogService.class, CoQuartzMetrics.class})
    public Object coQuartzJdbcMetricsBinder(AsyncTaskLogService asyncTaskLogService,
                                             TaskLogRepository taskLogRepository,
                                             CoQuartzMetrics metrics) {
        metrics.bindLogPipeline(asyncTaskLogService);
        metrics.bindReliableAudit(taskLogRepository);
        return new Object();
    }

    @Bean
    @ConditionalOnMissingBean(ReliableAuditService.class)
    public ReliableAuditService reliableAuditService(TaskLogRepository taskLogRepository,
                                                     ApplicationEventPublisher eventPublisher) {
        return new JdbcReliableAuditService(taskLogRepository, eventPublisher);
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnProperty(prefix = "co-quartz.log", name = "reliable-audit", havingValue = "true")
    public ApplicationRunner reliableAuditRecoveryRunner(Scheduler scheduler, ReliableAuditService reliableAuditService,
                                                         CoQuartzProperties properties) {
        return args -> {
            long thresholdMs = properties.getLog().getReliableAuditRecoveryThresholdMs();
            if (thresholdMs <= 0) return;
            reliableAuditService.recoverInterruptedBefore(java.time.LocalDateTime.now().minusNanos(thresholdMs * 1_000_000),
                    scheduler.getSchedulerInstanceId(), scheduler.getMetaData().isJobStoreClustered());
        };
    }

    @Bean
    public TaskLogService taskLogService(TaskLogRepository taskLogRepository) {
        return new TaskLogService(taskLogRepository);
    }

    @Bean
    public SchemaInitializer schemaInitializer(JdbcTemplate coQuartzJdbcTemplate, CoQuartzProperties properties) {
        return new SchemaInitializer(coQuartzJdbcTemplate, properties);
    }

    @Bean
    public SchemaInitializerRunner schemaInitializerRunner(SchemaInitializer schemaInitializer) {
        return new SchemaInitializerRunner(schemaInitializer);
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    public LogCleanupRegistrar logCleanupRegistrar(Scheduler scheduler, TaskLogRepository taskLogRepository, CoQuartzProperties properties) {
        LogCleanupRegistrar registrar = new LogCleanupRegistrar(scheduler, taskLogRepository, properties);
        registrar.register();
        return registrar;
    }

    @Bean
    @ConditionalOnBean(AsyncTaskLogService.class)
    public AlertEventPublisher alertEventPublisher(ApplicationEventPublisher eventPublisher,
                                                     CoQuartzProperties properties,
                                                     TaskLogRepository taskLogRepository,
                                                     @Qualifier("coQuartzAlertExecutor") java.util.concurrent.Executor alertExecutor) {
        return new AlertEventPublisher(eventPublisher, properties, alertExecutor);
    }

    @Bean
    @ConditionalOnBean(AsyncTaskLogService.class)
    public CoQuartzJobListener coQuartzJobListener(AsyncTaskLogService asyncTaskLogService,
                                                     Scheduler scheduler,
                                                     ObjectProvider<CoQuartzMetrics> metricsProvider,
                                                     ObjectProvider<AlertEventPublisher> alertEventPublisherProvider,
                                                     CoQuartzProperties properties,
                                                     ObjectProvider<LogSanitizer> logSanitizerProvider,
                                                     ObjectProvider<ReliableAuditService> reliableAuditServiceProvider) throws SchedulerException {
        CoQuartzMetrics metrics = metricsProvider.getIfAvailable();
        AlertEventPublisher alertEventPublisher = alertEventPublisherProvider.getIfAvailable();
        CoQuartzJobListener listener = new CoQuartzJobListener(asyncTaskLogService, metrics, alertEventPublisher,
                properties, logSanitizerProvider.getIfAvailable(), reliableAuditServiceProvider.getIfAvailable());
        scheduler.getListenerManager().addJobListener(listener);
        return listener;
    }

    public static class SchemaInitializerRunner {
        public SchemaInitializerRunner(SchemaInitializer schemaInitializer) {
            schemaInitializer.initialize();
        }
    }
}
