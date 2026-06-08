package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.jdbc.job.LogCleanupRegistrar;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.jdbc.service.JdbcAsyncTaskLogService;
import io.github.cococzl.coquartz.jdbc.service.JdbcTaskMonitoringService;
import io.github.cococzl.coquartz.jdbc.service.TaskLogService;
import io.github.cococzl.coquartz.listener.CoQuartzJobListener;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import io.github.cococzl.coquartz.service.TaskMonitoringService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(io.github.cococzl.coquartz.config.CoQuartzCoreAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "co-quartz.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoQuartzProperties.class)
public class CoQuartzJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate coQuartzJdbcTemplate(DataSource dataSource) {
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
                                                             CoQuartzProperties properties) {
        CoQuartzProperties.AsyncConfig asyncConfig = properties.getAsync();
        return new JdbcAsyncTaskLogService(taskLogRepository,
                asyncConfig.getLogQueueCapacity(),
                asyncConfig.getLogBatchSize(),
                asyncConfig.getLogFlushIntervalMs());
    }

    @Bean
    @ConditionalOnMissingBean(TaskMonitoringService.class)
    public JdbcTaskMonitoringService jdbcTaskMonitoringService(TaskLogRepository taskLogRepository) {
        return new JdbcTaskMonitoringService(taskLogRepository);
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
                                                     TaskLogRepository taskLogRepository) {
        return new AlertEventPublisher(eventPublisher, taskLogRepository, properties);
    }

    @Bean
    @ConditionalOnBean(AsyncTaskLogService.class)
    public CoQuartzJobListener coQuartzJobListener(AsyncTaskLogService asyncTaskLogService,
                                                     Scheduler scheduler,
                                                     ObjectProvider<CoQuartzMetrics> metricsProvider,
                                                     ObjectProvider<AlertEventPublisher> alertEventPublisherProvider) throws SchedulerException {
        CoQuartzMetrics metrics = metricsProvider.getIfAvailable();
        AlertEventPublisher alertEventPublisher = alertEventPublisherProvider.getIfAvailable();
        CoQuartzJobListener listener = new CoQuartzJobListener(asyncTaskLogService, metrics, alertEventPublisher);
        scheduler.getListenerManager().addJobListener(listener);
        return listener;
    }

    public static class SchemaInitializerRunner {
        public SchemaInitializerRunner(SchemaInitializer schemaInitializer) {
            schemaInitializer.initialize();
        }
    }
}