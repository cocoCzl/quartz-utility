package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.PageResult;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskLogQuery;
import io.github.cococzl.coquartz.dto.TaskStatistics;
import io.github.cococzl.coquartz.service.TaskLogRepository;

public class TaskLogService {

    private final TaskLogRepository taskLogRepository;

    public TaskLogService(TaskLogRepository taskLogRepository) {
        this.taskLogRepository = taskLogRepository;
    }

    public PageResult<TaskExecutionLog> pageLogs(TaskLogQuery query) {
        return taskLogRepository.pageLogs(query);
    }

    public TaskStatistics statistics() {
        return taskLogRepository.statistics();
    }

    public TaskStatistics statistics(String jobKey) {
        return taskLogRepository.statistics(jobKey);
    }

    public int cleanup(int daysToKeep) {
        return taskLogRepository.cleanup(daysToKeep);
    }
}