package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskStatistics;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import io.github.cococzl.coquartz.service.TaskMonitoringService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class JdbcTaskMonitoringService implements TaskMonitoringService {

    private final TaskLogRepository taskLogRepository;

    public JdbcTaskMonitoringService(TaskLogRepository taskLogRepository) {
        this.taskLogRepository = taskLogRepository;
    }

    @Override
    public TaskStatistics getTaskStatistics() {
        return taskLogRepository.statistics();
    }

    @Override
    public List<TaskExecutionLog> getTaskExecutionHistory(String jobKey, int limit) {
        return taskLogRepository.latestLogs(jobKey, limit);
    }

    @Override
    public List<TaskExecutionLog> getRecentFailedTasks(int limit) {
        return taskLogRepository.failedLogs(limit);
    }

    @Override
    public int cleanupLogs(int daysToKeep) {
        return taskLogRepository.cleanup(daysToKeep);
    }

    @Override
    public List<TaskExecutionLog> getTaskExecutionsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return taskLogRepository.findByTimeRange(start, end);
    }

    @Override
    public Map<String, Double> getAverageExecutionTimeByJob() {
        return taskLogRepository.avgExecutionTimeByJob();
    }
}