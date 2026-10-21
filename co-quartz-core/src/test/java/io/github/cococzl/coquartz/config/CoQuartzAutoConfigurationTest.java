package io.github.cococzl.coquartz.config;

import io.github.cococzl.coquartz.core.CoQuartzScheduler;
import io.github.cococzl.coquartz.listener.CoQuartzJobListener;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CoQuartzAutoConfigurationTest {

    @Test
    void coreStartsWithoutJdbcOrDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QuartzAutoConfiguration.class,
                        CoQuartzCoreAutoConfiguration.class))
                .withPropertyValues(
                        "spring.quartz.auto-startup=false",
                        "co-quartz.annotation.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(Scheduler.class);
                    assertThat(context).hasSingleBean(CoQuartzScheduler.class);
                    assertThat(context).hasSingleBean(CoQuartzJobListener.class);
                    assertThat(context).doesNotHaveBean(TaskExecutionLogWriter.class);
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class);
                });
    }

    @Test
    void reliableAuditWithoutServiceFailsAtStartup() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                QuartzAutoConfiguration.class, CoQuartzCoreAutoConfiguration.class))
                .withPropertyValues("spring.quartz.auto-startup=false", "co-quartz.annotation.enabled=false",
                        "co-quartz.log.reliable-audit=true", "co-quartz.log.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("ReliableAuditService");
                });
    }

    @Test
    void unsupportedQuartzStringOnlyJobDataModeFailsFast() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                QuartzAutoConfiguration.class, CoQuartzCoreAutoConfiguration.class))
                .withPropertyValues("spring.quartz.auto-startup=false", "co-quartz.annotation.enabled=false",
                        "spring.quartz.properties.org.quartz.jobStore.useProperties=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("useProperties");
                });
    }

    @Test
    void monitoringDisabledBacksOffFromMicrometerMetrics() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CoQuartzMicrometerAutoConfiguration.class))
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("co-quartz.monitoring.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CoQuartzMetrics.class));
    }
}
