package io.github.cococzl.coquartz.config;

import io.github.cococzl.coquartz.actuator.CoQuartzEndpoint;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.github.cococzl.coquartz.service.TaskMonitoringService;
import io.github.cococzl.coquartz.service.TaskQueryService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = CoQuartzCoreAutoConfiguration.class)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnBean(TaskQueryService.class)
public class CoQuartzActuatorAutoConfiguration {
    @Bean
    public CoQuartzEndpoint coQuartzEndpoint(TaskQueryService taskQueryService,
                                              ObjectProvider<TaskExecutionLogWriter> pipeline,
                                              ObjectProvider<TaskMonitoringService> monitoring,
                                              ObjectProvider<TaskLogRepository> repository) {
        return new CoQuartzEndpoint(taskQueryService, pipeline, monitoring, repository);
    }
}
