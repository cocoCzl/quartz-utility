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

    /** Updates an existing reliable-audit lifecycle record. Implementations must be idempotent. */
    default void updateLifecycle(TaskExecutionLog log) {
        throw new UnsupportedOperationException("Reliable audit is not supported by this task-log repository");
    }

    default List<TaskExecutionLog> findStartedBefore(LocalDateTime cutoff) {
        throw new UnsupportedOperationException("Reliable audit is not supported by this task-log repository");
    }

    default boolean markInterrupted(String id, LocalDateTime endTime) {
        throw new UnsupportedOperationException("Reliable audit is not supported by this task-log repository");
    }

    default long countStarted() {
        throw new UnsupportedOperationException("Reliable audit is not supported by this task-log repository");
    }

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
