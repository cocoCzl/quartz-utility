package com.coco.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coco.core.CoQuartzScheduler;
import com.coco.service.AsyncTaskLogService;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class QuartzAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(QuartzAutoConfiguration.class));

    @Test
    void createsSchedulerFacadeWithoutRequiringDataSource() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CoQuartzScheduler.class);
            assertThat(context).doesNotHaveBean(AsyncTaskLogService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class SchedulerConfiguration {
        @Bean(destroyMethod = "shutdown")
        Scheduler scheduler() throws Exception {
            return StdSchedulerFactory.getDefaultScheduler();
        }
    }
}
