package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskStatistics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TaskMonitoringService {

    TaskStatistics getTaskStatistics();

    List<TaskExecutionLog> getTaskExecutionHistory(String jobKey, int limit);

    List<TaskExecutionLog> getRecentFailedTasks(int limit);

    int cleanupLogs(int daysToKeep);

    List<TaskExecutionLog> getTaskExecutionsByTimeRange(LocalDateTime start, LocalDateTime end);

    Map<String, Double> getAverageExecutionTimeByJob();
}