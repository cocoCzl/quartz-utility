package io.github.cococzl.coquartz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "co-quartz")
public class CoQuartzProperties {

    private LogConfig log = new LogConfig();
    private AsyncConfig async = new AsyncConfig();
    private MonitoringConfig monitoring = new MonitoringConfig();
    private TimeoutPoolConfig timeoutPool = new TimeoutPoolConfig();
    private AnnotationConfig annotation = new AnnotationConfig();

    public LogConfig getLog() {
        return log;
    }

    public void setLog(LogConfig log) {
        this.log = log;
    }

    public AsyncConfig getAsync() {
        return async;
    }

    public void setAsync(AsyncConfig async) {
        this.async = async;
    }

    public MonitoringConfig getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringConfig monitoring) {
        this.monitoring = monitoring;
    }

    public TimeoutPoolConfig getTimeoutPool() {
        return timeoutPool;
    }

    public void setTimeoutPool(TimeoutPoolConfig timeoutPool) {
        this.timeoutPool = timeoutPool;
    }

    public AnnotationConfig getAnnotation() {
        return annotation;
    }

    public void setAnnotation(AnnotationConfig annotation) {
        this.annotation = annotation;
    }

    public static class LogConfig {
        private boolean enabled = true;
        private int retentionDays = 30;
        private String cleanupCron = "0 0 2 * * ?";
        private boolean autoCreateTable = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public String getCleanupCron() { return cleanupCron; }
        public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }
        public boolean isAutoCreateTable() { return autoCreateTable; }
        public void setAutoCreateTable(boolean autoCreateTable) { this.autoCreateTable = autoCreateTable; }
    }

    public static class AsyncConfig {
        private boolean enabled = true;
        private int logQueueCapacity = 1000;
        private int logBatchSize = 100;
        private long logFlushIntervalMs = 1000;
        private long shutdownFlushTimeoutMs = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getLogQueueCapacity() { return logQueueCapacity; }
        public void setLogQueueCapacity(int logQueueCapacity) { this.logQueueCapacity = logQueueCapacity; }
        public int getLogBatchSize() { return logBatchSize; }
        public void setLogBatchSize(int logBatchSize) { this.logBatchSize = logBatchSize; }
        public long getLogFlushIntervalMs() { return logFlushIntervalMs; }
        public void setLogFlushIntervalMs(long logFlushIntervalMs) { this.logFlushIntervalMs = logFlushIntervalMs; }
        public long getShutdownFlushTimeoutMs() { return shutdownFlushTimeoutMs; }
        public void setShutdownFlushTimeoutMs(long shutdownFlushTimeoutMs) { this.shutdownFlushTimeoutMs = shutdownFlushTimeoutMs; }
    }

    public static class MonitoringConfig {
        private boolean enabled = true;
        private long slowTaskThresholdMs = 30000;
        private int consecutiveFailureThreshold = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSlowTaskThresholdMs() { return slowTaskThresholdMs; }
        public void setSlowTaskThresholdMs(long slowTaskThresholdMs) { this.slowTaskThresholdMs = slowTaskThresholdMs; }
        public int getConsecutiveFailureThreshold() { return consecutiveFailureThreshold; }
        public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) { this.consecutiveFailureThreshold = consecutiveFailureThreshold; }
    }

    public static class TimeoutPoolConfig {
        private int coreSize = 2;
        private int maxSize = 5;

        public int getCoreSize() { return coreSize; }
        public void setCoreSize(int coreSize) { this.coreSize = coreSize; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    public static class AnnotationConfig {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}