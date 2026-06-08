package io.github.cococzl.coquartz.config;

import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(CoQuartzProperties.class)
public class CoQuartzMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public CoQuartzMetrics coQuartzMetrics(MeterRegistry meterRegistry) {
        return new CoQuartzMetrics(meterRegistry);
    }
}