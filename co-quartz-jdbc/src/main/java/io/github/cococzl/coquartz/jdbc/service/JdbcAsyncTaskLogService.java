package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.*;
import io.github.cococzl.coquartz.event.*;
import io.github.cococzl.coquartz.service.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Single-consumer bounded pipeline. JDBC cancellation is cooperative; unresolved in-flight work stays visible. */
public class JdbcAsyncTaskLogService implements AsyncTaskLogService {
    private record Pending(TaskExecutionLog entry, int failures) { }
    private final TaskLogRepository repository;
    private final LinkedBlockingQueue<Pending> queue;
    private final ScheduledThreadPoolExecutor consumer;
    private final int batchSize, maxWriteRetries;
    private final long shutdownTimeoutMs;
    private final Object lifecycle = new Object();
    private volatile boolean accepting = true;
    private volatile Thread consumerThread;
    private final AtomicBoolean requested = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong(), failed = new AtomicLong(), permanent = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong(), accepted = new AtomicLong(), unflushed = new AtomicLong();
    private final ApplicationEventPublisher events;
    private final ObjectProvider<AlertEventPublisher> alertProvider;

    public JdbcAsyncTaskLogService(TaskLogRepository repository, int capacity, int batchSize, long intervalMs) {
        this(repository, capacity, batchSize, intervalMs, 10000, 3, null);
    }
    public JdbcAsyncTaskLogService(TaskLogRepository repository, int capacity, int batchSize, long intervalMs,
                                   long shutdownMs, int retries, ApplicationEventPublisher events) {
        this(repository, capacity, batchSize, intervalMs, shutdownMs, retries, events, null);
    }
    public JdbcAsyncTaskLogService(TaskLogRepository repository, int capacity, int batchSize, long intervalMs,
                                   long shutdownMs, int retries, ApplicationEventPublisher events,
                                   ObjectProvider<AlertEventPublisher> alertProvider) {
        this.repository = repository; this.queue = new LinkedBlockingQueue<>(capacity);
        this.batchSize = batchSize; this.shutdownTimeoutMs = shutdownMs;
        this.maxWriteRetries = Math.max(0, retries); this.events = events; this.alertProvider = alertProvider;
        consumer = new ScheduledThreadPoolExecutor(1, action -> {
            Thread thread = new Thread(action, "co-quartz-log-flusher"); thread.setDaemon(true);
            consumerThread = thread; return thread;
        });
        consumer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        consumer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        consumer.scheduleWithFixedDelay(this::drainOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }
    public void logTaskExecutionAsync(TaskExecutionLog entry) {
        boolean rejected;
        synchronized (lifecycle) {
            rejected = !accepting || !queue.offer(new Pending(entry, 0));
            if (!rejected) accepted.incrementAndGet();
        }
        if (rejected) { publish(TaskLogPipelineEvent.Type.QUEUE_FULL, dropped.incrementAndGet()); return; }
        if (queue.size() >= batchSize) requestFlush();
    }
    private void requestFlush() {
        if (!requested.compareAndSet(false, true)) return;
        try { consumer.execute(() -> { try { drainOnce(); } finally { requested.set(false); } }); }
        catch (RejectedExecutionException closing) { requested.set(false); }
    }
    public void flushLogsImmediately() {
        if (Thread.currentThread() == consumerThread) return;
        try { consumer.submit(this::drainOnce).get(); }
        catch (RejectedExecutionException closed) { }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (ExecutionException failure) { throw new IllegalStateException("Log flush failed", failure.getCause()); }
    }
    private void drainOnce() {
        int budget = queue.size(); // retries are deferred to the next flush; one faulty row cannot spin forever
        while (budget > 0) {
            List<Pending> batch = new ArrayList<>();
            queue.drainTo(batch, Math.min(batchSize, budget));
            if (batch.isEmpty()) return;
            budget -= batch.size(); inFlight.set(batch.size());
            List<TaskExecutionLog> failures;
            try { failures = repository.insertBatch(batch.stream().map(Pending::entry).toList()); }
            catch (RuntimeException failure) { failures = batch.stream().map(Pending::entry).toList(); }
            Set<TaskExecutionLog> failedEntries = Collections.newSetFromMap(new IdentityHashMap<>());
            failedEntries.addAll(failures);
            for (Pending pending : batch) {
                if (!failedEntries.contains(pending.entry())) persisted.incrementAndGet();
                else {
                    failed.incrementAndGet();
                    if (pending.failures() >= maxWriteRetries) {
                        publish(TaskLogPipelineEvent.Type.PERMANENT_WRITE_FAILURE, permanent.incrementAndGet());
                    } else if (!queue.offer(new Pending(pending.entry(), pending.failures() + 1))) {
                        publish(TaskLogPipelineEvent.Type.QUEUE_FULL, dropped.incrementAndGet());
                    }
                }
                inFlight.decrementAndGet();
            }
        }
    }
    public void shutdown() {
        synchronized (lifecycle) {
            if (!accepting) return;
            accepting = false;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownTimeoutMs);
        if (shutdownTimeoutMs > 0) {
            try {
                consumer.execute(() -> {
                    while (!queue.isEmpty() && System.nanoTime() < deadline) drainOnce();
                });
            } catch (RejectedExecutionException ignored) { }
        }
        consumer.shutdown();
        try {
            if (!consumer.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) consumer.shutdownNow();
        } catch (InterruptedException interrupted) { consumer.shutdownNow(); Thread.currentThread().interrupt(); }
        long remaining = (long) queue.size() + inFlight.get();
        unflushed.set(remaining);
        if (remaining > 0) publish(TaskLogPipelineEvent.Type.SHUTDOWN_UNFLUSHED, remaining);
    }
    public int getQueueSize() { return queue.size(); }
    public AsyncLogPipelineStatus getPipelineStatus() {
        return new AsyncLogPipelineStatus(queue.size(), dropped.get(), failed.get(), permanent.get(),
                unflushed.get(), inFlight.get(), persisted.get(), accepted.get());
    }
    private void publish(TaskLogPipelineEvent.Type type, long count) {
        try {
            AlertEventPublisher alert = alertProvider == null ? null : alertProvider.getIfAvailable();
            if (alert != null) alert.publishLogPipeline(type, count);
            else if (events != null) events.publishEvent(new TaskLogPipelineEvent(this, type, count));
        } catch (RuntimeException ignored) { /* Pipeline counters remain authoritative. */ }
    }
}
