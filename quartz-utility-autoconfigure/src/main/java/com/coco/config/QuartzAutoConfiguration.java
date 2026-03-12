package com.coco.config;

import com.coco.core.CoQuartzScheduler;
import jakarta.annotation.PostConstruct;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

/**
 * Quartz Utility 自动配置类
 */
@AutoConfiguration
@EnableConfigurationProperties(QuartzUtilityProperties.class)
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = "com.coco")
public class QuartzAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QuartzAutoConfiguration.class);

    @Autowired
    private QuartzUtilityProperties properties;

    @PostConstruct
    public void init() {
        logger.info("Quartz Utility auto-configuration initialized");
    }

    /**
     * 配置 Quartz 专用的 JdbcTemplate
     *
     * @param dataSource 数据源
     * @return JdbcTemplate 实例
     */
    @Lazy
    @Bean(name = "quartzJdbcTemplate")
    JdbcTemplate jdbcTemplate(@Autowired DataSource dataSource) {
        logger.info("Creating quartzJdbcTemplate bean");
        return new JdbcTemplate(dataSource);
    }

    /**
     * 配置 CoQuartzScheduler
     *
     * @param scheduler Quartz 调度器
     * @return CoQuartzScheduler 实例
     */
    @Bean
    @Primary
    CoQuartzScheduler coQuartzScheduler(@Autowired Scheduler scheduler) {
        logger.info("Creating CoQuartzScheduler bean");
        return new CoQuartzScheduler(scheduler);
    }

    /**
     * 配置异步执行器，用于异步日志记录
     *
     * @return 异步执行器
     */
    @Bean(name = "quartzAsyncExecutor")
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
}
