package io.github.cococzl.coquartz.config;

import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "co-quartz.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoQuartzProperties.class)
public class CoQuartzMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(CoQuartzMetrics.class)
    public CoQuartzMetrics coQuartzMetrics(MeterRegistry meterRegistry, CoQuartzProperties properties) {
        return new CoQuartzMetrics(meterRegistry, properties.getMonitoring().getMaxMetricJobTags());
    }
}
