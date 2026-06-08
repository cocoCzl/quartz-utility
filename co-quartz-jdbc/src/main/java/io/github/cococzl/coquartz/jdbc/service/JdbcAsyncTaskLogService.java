package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class JdbcAsyncTaskLogService implements AsyncTaskLogService {

    private static final Logger log = LoggerFactory.getLogger(JdbcAsyncTaskLogService.class);

    private final TaskLogRepository taskLogRepository;
    private final LinkedBlockingQueue<TaskExecutionLog> queue;
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final long flushIntervalMs;

    public JdbcAsyncTaskLogService(TaskLogRepository taskLogRepository,
                                     int queueCapacity,
                                     int batchSize,
                                     long flushIntervalMs) {
        this.taskLogRepository = taskLogRepository;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "co-quartz-log-flusher");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleWithFixedDelay(this::flushLogsInternal, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void logTaskExecutionAsync(TaskExecutionLog taskLog) {
        if (!queue.offer(taskLog)) {
            log.warn("Async log queue is full, dropping log entry for job: {}", taskLog.getJobKey());
        }
    }

    @Override
    public void flushLogsImmediately() {
        flushLogsInternal();
    }

    @Override
    public void shutdown() {
        flushLogsInternal();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    private void flushLogsInternal() {
        List<TaskExecutionLog> batch = new ArrayList<>();
        queue.drainTo(batch, batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (TaskExecutionLog taskLog : batch) {
            try {
                taskLogRepository.insert(taskLog);
            } catch (Exception e) {
                log.error("Failed to insert task log for job: {}", taskLog.getJobKey(), e);
                if (!queue.offer(taskLog)) {
                    log.warn("Failed to re-queue log entry for job: {}", taskLog.getJobKey());
                }
            }
        }
    }
}