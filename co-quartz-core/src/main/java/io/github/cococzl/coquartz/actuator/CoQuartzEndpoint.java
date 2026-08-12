package io.github.cococzl.coquartz.actuator;

import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskInfo;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.github.cococzl.coquartz.service.TaskMonitoringService;
import io.github.cococzl.coquartz.service.TaskQueryService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

/** Optional read-only operational summary. Application security controls endpoint exposure. */
@Endpoint(id = "coquartz")
public class CoQuartzEndpoint {
    private final TaskQueryService taskQueryService;
    private final ObjectProvider<TaskExecutionLogWriter> logPipeline;
    private final ObjectProvider<TaskMonitoringService> monitoring;
    private final ObjectProvider<TaskLogRepository> repository;

    public CoQuartzEndpoint(TaskQueryService taskQueryService, ObjectProvider<TaskExecutionLogWriter> logPipeline,
                            ObjectProvider<TaskMonitoringService> monitoring) {
        this(taskQueryService, logPipeline, monitoring, null);
    }

    public CoQuartzEndpoint(TaskQueryService taskQueryService, ObjectProvider<TaskExecutionLogWriter> logPipeline,
                            ObjectProvider<TaskMonitoringService> monitoring, ObjectProvider<TaskLogRepository> repository) {
        this.taskQueryService = taskQueryService;
        this.logPipeline = logPipeline;
        this.monitoring = monitoring;
        this.repository = repository;
    }

    @ReadOperation
    public Map<String, Object> summary() throws Exception {
        List<Map<String, Object>> tasks = taskQueryService.listJobs().stream().map(this::task).toList();
        TaskExecutionLogWriter pipeline = logPipeline.getIfAvailable();
        AsyncLogPipelineStatus pipelineStatus = pipeline == null ? null : pipeline.getPipelineStatus();
        TaskMonitoringService taskMonitoring = monitoring.getIfAvailable();
        List<Map<String, Object>> recentFailures = taskMonitoring == null ? List.of()
                : taskMonitoring.getRecentFailedTasks(10).stream().map(this::failure).toList();
        TaskLogRepository taskLogRepository = repository == null ? null : repository.getIfAvailable();
        long auditStarted = 0;
        if (taskLogRepository != null) {
            try { auditStarted = taskLogRepository.countStarted(); } catch (UnsupportedOperationException ignored) { }
        }
        return Map.of("tasks", tasks, "recentFailures", recentFailures, "auditStarted", auditStarted,
                "logPipeline", pipelineStatus == null ? Map.of("available", false) : Map.of(
                        "available", true, "queueSize", pipelineStatus.queueSize(), "dropped", pipelineStatus.droppedCount(),
                        "writeFailures", pipelineStatus.writeFailureCount(), "unflushed", pipelineStatus.unflushedCount()));
    }

    private Map<String, Object> task(TaskInfo info) {
        return Map.of("name", info.getJobName(), "group", info.getJobGroup(), "source", String.valueOf(info.getSource()),
                "triggerState", String.valueOf(info.getTriggerState()), "nextFireTime", String.valueOf(info.getNextFireTime()));
    }

    private Map<String, Object> failure(TaskExecutionLog log) {
        return Map.of("jobKey", log.getJobKey(), "executionId", String.valueOf(log.getExecutionId()),
                "state", String.valueOf(log.getExecState()), "endTime", String.valueOf(log.getEndTime()));
    }
}
