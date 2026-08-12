package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.jdbc.job.LogCleanupRegistrar;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.jdbc.service.JdbcAsyncTaskLogService;
import io.github.cococzl.coquartz.jdbc.service.JdbcTaskMonitoringService;
import io.github.cococzl.coquartz.jdbc.service.JdbcReliableAuditService;
import io.github.cococzl.coquartz.jdbc.service.JdbcSynchronousTaskLogWriter;
import io.github.cococzl.coquartz.jdbc.service.TaskLogService;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import io.github.cococzl.coquartz.service.TaskMonitoringService;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
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
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(io.github.cococzl.coquartz.config.CoQuartzCoreAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "co-quartz.log", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CoQuartzProperties.class)
public class CoQuartzJdbcAutoConfiguration {

    @Bean(name = "coQuartzLogDataSource")
    @ConditionalOnMissingBean(name = "coQuartzLogDataSource")
    @ConditionalOnProperty(prefix = "co-quartz.log.datasource", name = "url")
    public DataSource coQuartzLogDataSource(CoQuartzProperties properties) {
        CoQuartzProperties.LogConfig.DataSourceConfig config = properties.getLog().getDatasource();
        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .url(config.getUrl())
                .username(config.getUsername())
                .password(config.getPassword());
        if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
            builder.driverClassName(config.getDriverClassName());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "coQuartzJdbcTemplate")
    public JdbcTemplate coQuartzJdbcTemplate(
            @Qualifier("coQuartzLogDataSource") ObjectProvider<DataSource> logDataSource,
            ObjectProvider<DataSource> dataSources) {
        DataSource dedicated = logDataSource.getIfAvailable();
        if (dedicated != null) return new JdbcTemplate(dedicated);
        java.util.List<DataSource> candidates = dataSources.orderedStream().toList();
        if (candidates.size() == 1) return new JdbcTemplate(candidates.get(0));
        throw new IllegalStateException(candidates.isEmpty()
                ? "Co-Quartz logging is enabled but no DataSource is available; configure co-quartz.log.datasource.url"
                : "Co-Quartz logging found multiple DataSources; provide a coQuartzLogDataSource bean or configure co-quartz.log.datasource.url");
    }

    @Bean
    @ConditionalOnMissingBean(TaskLogRepository.class)
    public JdbcTaskLogRepository jdbcTaskLogRepository(@Qualifier("coQuartzJdbcTemplate") JdbcTemplate coQuartzJdbcTemplate) {
        return new JdbcTaskLogRepository(coQuartzJdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TaskExecutionLogWriter.class)
    @ConditionalOnProperty(prefix = "co-quartz.async", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnMissingBean(TaskExecutionLogWriter.class)
    @ConditionalOnProperty(prefix = "co-quartz.async", name = "enabled", havingValue = "false")
    public JdbcSynchronousTaskLogWriter jdbcSynchronousTaskLogWriter(TaskLogRepository taskLogRepository) {
        return new JdbcSynchronousTaskLogWriter(taskLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean(TaskMonitoringService.class)
    public JdbcTaskMonitoringService jdbcTaskMonitoringService(TaskLogRepository taskLogRepository) {
        return new JdbcTaskMonitoringService(taskLogRepository);
    }

    @Bean
    @ConditionalOnBean({TaskExecutionLogWriter.class, CoQuartzMetrics.class})
    @ConditionalOnMissingBean(name = "coQuartzJdbcMetricsBinder")
    public Object coQuartzJdbcMetricsBinder(TaskExecutionLogWriter logWriter,
                                             TaskLogRepository taskLogRepository,
                                             CoQuartzMetrics metrics) {
        metrics.bindLogPipeline(logWriter);
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
    @ConditionalOnMissingBean(name = "reliableAuditRecoveryRunner")
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
    @ConditionalOnMissingBean(TaskLogService.class)
    public TaskLogService taskLogService(TaskLogRepository taskLogRepository) {
        return new TaskLogService(taskLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean(SchemaInitializer.class)
    public SchemaInitializer schemaInitializer(@Qualifier("coQuartzJdbcTemplate") JdbcTemplate coQuartzJdbcTemplate, CoQuartzProperties properties) {
        return new SchemaInitializer(coQuartzJdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SchemaInitializerRunner.class)
    public SchemaInitializerRunner schemaInitializerRunner(SchemaInitializer schemaInitializer) {
        return new SchemaInitializerRunner(schemaInitializer);
    }

    @Bean
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnMissingBean(LogCleanupRegistrar.class)
    public LogCleanupRegistrar logCleanupRegistrar(Scheduler scheduler, CoQuartzProperties properties) {
        LogCleanupRegistrar registrar = new LogCleanupRegistrar(scheduler, properties);
        registrar.register();
        return registrar;
    }

    public static class SchemaInitializerRunner {
        public SchemaInitializerRunner(SchemaInitializer schemaInitializer) {
            schemaInitializer.initialize();
        }
    }
}
