package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.event.TaskLogPipelineEvent;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class JdbcAsyncTaskLogService implements AsyncTaskLogService {

    private static final Logger log = LoggerFactory.getLogger(JdbcAsyncTaskLogService.class);

    private final TaskLogRepository taskLogRepository;
    private final LinkedBlockingQueue<TaskExecutionLog> queue;
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final long flushIntervalMs;
    private final long shutdownFlushTimeoutMs;
    private final int maxWriteRetries;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<AlertEventPublisher> alertEventPublisherProvider;
    private final ConcurrentHashMap<String, Integer> writeAttempts = new ConcurrentHashMap<>();
    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong writeFailureCount = new AtomicLong();
    private final AtomicLong permanentFailureCount = new AtomicLong();
    private final AtomicLong unflushedCount = new AtomicLong();

    public JdbcAsyncTaskLogService(TaskLogRepository taskLogRepository,
                                     int queueCapacity,
                                     int batchSize,
                                     long flushIntervalMs) {
        this(taskLogRepository, queueCapacity, batchSize, flushIntervalMs, 10_000, 3, null);
    }

    public JdbcAsyncTaskLogService(TaskLogRepository taskLogRepository, int queueCapacity, int batchSize,
                                   long flushIntervalMs, long shutdownFlushTimeoutMs, int maxWriteRetries,
                                   ApplicationEventPublisher eventPublisher) {
        this(taskLogRepository, queueCapacity, batchSize, flushIntervalMs, shutdownFlushTimeoutMs, maxWriteRetries,
                eventPublisher, null);
    }

    public JdbcAsyncTaskLogService(TaskLogRepository taskLogRepository, int queueCapacity, int batchSize,
                                   long flushIntervalMs, long shutdownFlushTimeoutMs, int maxWriteRetries,
                                   ApplicationEventPublisher eventPublisher,
                                   ObjectProvider<AlertEventPublisher> alertEventPublisherProvider) {
        this.taskLogRepository = taskLogRepository;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.shutdownFlushTimeoutMs = shutdownFlushTimeoutMs;
        this.maxWriteRetries = Math.max(0, maxWriteRetries);
        this.eventPublisher = eventPublisher;
        this.alertEventPublisherProvider = alertEventPublisherProvider;
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
            long dropped = droppedCount.incrementAndGet();
            log.warn("Async log queue is full, dropping log entry for job: {}", taskLog.getJobKey());
            publish(TaskLogPipelineEvent.Type.QUEUE_FULL, dropped);
        }
    }

    @Override
    public void flushLogsImmediately() {
        flushLogsInternal();
    }

    @Override
    public void shutdown() {
        scheduler.shutdown();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownFlushTimeoutMs);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            flushLogsInternal();
        }
        try {
            long remainingMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            if (!scheduler.awaitTermination(remainingMs, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        long remaining = queue.size();
        unflushedCount.set(remaining);
        if (remaining > 0) {
            publish(TaskLogPipelineEvent.Type.SHUTDOWN_UNFLUSHED, remaining);
        }
    }

    @Override
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public AsyncLogPipelineStatus getPipelineStatus() {
        return new AsyncLogPipelineStatus(queue.size(), droppedCount.get(), writeFailureCount.get(),
                permanentFailureCount.get(), unflushedCount.get());
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
                writeAttempts.remove(taskLog.getId());
            } catch (Exception e) {
                writeFailureCount.incrementAndGet();
                log.error("Failed to insert task log for job: {}", taskLog.getJobKey(), e);
                int attempts = writeAttempts.merge(taskLog.getId(), 1, Integer::sum);
                if (attempts > maxWriteRetries) {
                    permanentFailureCount.incrementAndGet();
                    writeAttempts.remove(taskLog.getId());
                    publish(TaskLogPipelineEvent.Type.PERMANENT_WRITE_FAILURE, permanentFailureCount.get());
                } else if (!queue.offer(taskLog)) {
                    long dropped = droppedCount.incrementAndGet();
                    log.warn("Failed to re-queue log entry for job: {}", taskLog.getJobKey());
                    publish(TaskLogPipelineEvent.Type.QUEUE_FULL, dropped);
                }
            }
        }
    }

    private void publish(TaskLogPipelineEvent.Type type, long count) {
        AlertEventPublisher alertPublisher = alertEventPublisherProvider == null ? null : alertEventPublisherProvider.getIfAvailable();
        if (alertPublisher != null) {
            alertPublisher.publishLogPipeline(type, count);
            return;
        }
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new TaskLogPipelineEvent(this, type, count));
        } catch (Exception eventFailure) {
            log.warn("Failed to publish execution-log pipeline event {}", type, eventFailure);
        }
    }
}
