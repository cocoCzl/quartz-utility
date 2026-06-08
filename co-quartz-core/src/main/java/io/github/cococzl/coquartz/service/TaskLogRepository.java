package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.PageResult;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskLogQuery;
import io.github.cococzl.coquartz.dto.TaskStatistics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TaskLogRepository {

    void insert(TaskExecutionLog log);

    PageResult<TaskExecutionLog> pageLogs(TaskLogQuery query);

    List<TaskExecutionLog> latestLogs(String jobKey, int limit);

    List<TaskExecutionLog> failedLogs(int limit);

    TaskStatistics statistics();

    TaskStatistics statistics(String jobKey);

    int cleanup(int daysToKeep);

    List<TaskExecutionLog> findRecentByJobKey(String jobKey, int limit);

    List<TaskExecutionLog> findByTimeRange(LocalDateTime start, LocalDateTime end);

    Map<String, Double> avgExecutionTimeByJob();
}