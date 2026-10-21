package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.jdbc.job.LogCleanupRegistrar;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Reconciles persisted cleanup jobs even when the JDBC logging configuration backs off. */
@AutoConfiguration(after = io.github.cococzl.coquartz.config.CoQuartzCoreAutoConfiguration.class)
@ConditionalOnBean(Scheduler.class)
@ConditionalOnProperty(prefix = "co-quartz.log", name = "enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(CoQuartzProperties.class)
public class DisabledCleanupAutoConfiguration {
    @Bean
    public org.springframework.beans.factory.SmartInitializingSingleton removeDisabledCleanup(
            Scheduler scheduler, CoQuartzProperties properties) {
        return () -> new LogCleanupRegistrar(scheduler, properties).register();
    }
}
