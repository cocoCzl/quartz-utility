package com.coco.config;

import com.coco.core.CoQuartzScheduler;
import com.coco.core.QuartzJobAnnotationProcessor;
import com.coco.core.TaskMonitoringService;
import com.coco.service.AsyncTaskLogService;
import com.coco.service.TaskAdminService;
import com.coco.service.TaskAlertService;
import com.coco.service.TaskLogService;
import com.coco.service.TaskQueryService;
import com.coco.service.impl.DefaultTaskAlertService;
import jakarta.annotation.PostConstruct;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration.class})
@ConditionalOnClass(Scheduler.class)
@EnableConfigurationProperties(QuartzUtilityProperties.class)
public class QuartzAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QuartzAutoConfiguration.class);

    private final QuartzUtilityProperties properties;

    public QuartzAutoConfiguration(QuartzUtilityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        logger.info("Quartz Utility auto-configuration initialized");
    }

    @Bean(name = "quartzJdbcTemplate")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = "quartzJdbcTemplate")
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        logger.info("Creating quartzJdbcTemplate bean");
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    @ConditionalOnBean(Scheduler.class)
    @ConditionalOnMissingBean
    CoQuartzScheduler coQuartzScheduler(Scheduler scheduler) {
        logger.info("Creating CoQuartzScheduler bean");
        return new CoQuartzScheduler(scheduler);
    }

    @Bean(name = "quartzAsyncExecutor")
    @ConditionalOnMissingBean(name = "quartzAsyncExecutor")
    @ConditionalOnProperty(prefix = "quartz-utility.async", name = "executor-enabled", havingValue = "true")
    public Executor quartzAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setQueueCapacity(properties.getAsync().getQueueCapacity());
        executor.setThreadNamePrefix(properties.getAsync().getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        logger.info("Quartz async executor configured: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                properties.getAsync().getCorePoolSize(),
                properties.getAsync().getMaxPoolSize(),
                properties.getAsync().getQueueCapacity());

        return executor;
    }

    @Bean
    @ConditionalOnBean(name = "quartzJdbcTemplate")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quartz-utility.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    AsyncTaskLogService asyncTaskLogService(@Qualifier("quartzJdbcTemplate") JdbcTemplate quartzJdbcTemplate,
            QuartzUtilityProperties properties) {
        return new AsyncTaskLogService(quartzJdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnBean(name = "quartzJdbcTemplate")
    @ConditionalOnMissingBean
    TaskMonitoringService taskMonitoringService(@Qualifier("quartzJdbcTemplate") JdbcTemplate quartzJdbcTemplate) {
        return new TaskMonitoringService(quartzJdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(name = "quartzJdbcTemplate")
    @ConditionalOnMissingBean
    TaskLogService taskLogService(@Qualifier("quartzJdbcTemplate") JdbcTemplate quartzJdbcTemplate) {
        return new TaskLogService(quartzJdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(CoQuartzScheduler.class)
    @ConditionalOnMissingBean
    TaskAdminService taskAdminService(CoQuartzScheduler scheduler) {
        return new TaskAdminService(scheduler);
    }

    @Bean
    @ConditionalOnBean(CoQuartzScheduler.class)
    @ConditionalOnMissingBean
    TaskQueryService taskQueryService(CoQuartzScheduler scheduler) {
        return new TaskQueryService(scheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    TaskAlertService taskAlertService() {
        return new DefaultTaskAlertService();
    }

    @Bean
    @ConditionalOnBean(CoQuartzScheduler.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "quartz-utility.annotation", name = "enabled", havingValue = "true", matchIfMissing = true)
    QuartzJobAnnotationProcessor quartzJobAnnotationProcessor(ApplicationContext applicationContext,
            CoQuartzScheduler scheduler) {
        return new QuartzJobAnnotationProcessor(applicationContext, scheduler);
    }
}
