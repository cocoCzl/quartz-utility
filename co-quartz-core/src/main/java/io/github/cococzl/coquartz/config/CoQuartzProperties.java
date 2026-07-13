package io.github.cococzl.coquartz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "co-quartz")
public class CoQuartzProperties {

    private LogConfig log = new LogConfig();
    private AsyncConfig async = new AsyncConfig();
    private MonitoringConfig monitoring = new MonitoringConfig();
    private TimeoutPoolConfig timeoutPool = new TimeoutPoolConfig();
    private AnnotationConfig annotation = new AnnotationConfig();
    private SchedulingConfig scheduling = new SchedulingConfig();

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

    public SchedulingConfig getScheduling() {
        return scheduling;
    }

    public void setScheduling(SchedulingConfig scheduling) {
        this.scheduling = scheduling;
    }

    public static class LogConfig {
        private boolean enabled = true;
        private int retentionDays = 30;
        private String cleanupCron = "0 0 2 * * ?";
        /** Development convenience only; production installations should run the versioned scripts explicitly. */
        private boolean autoCreateTable = false;
        private boolean captureStackTrace = true;
        private boolean reliableAudit = false;
        private long reliableAuditRecoveryThresholdMs = 60000;
        private DataSourceConfig datasource = new DataSourceConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public String getCleanupCron() { return cleanupCron; }
        public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }
        public boolean isAutoCreateTable() { return autoCreateTable; }
        public void setAutoCreateTable(boolean autoCreateTable) { this.autoCreateTable = autoCreateTable; }
        public boolean isCaptureStackTrace() { return captureStackTrace; }
        public void setCaptureStackTrace(boolean captureStackTrace) { this.captureStackTrace = captureStackTrace; }
        public boolean isReliableAudit() { return reliableAudit; }
        public void setReliableAudit(boolean reliableAudit) { this.reliableAudit = reliableAudit; }
        public long getReliableAuditRecoveryThresholdMs() { return reliableAuditRecoveryThresholdMs; }
        public void setReliableAuditRecoveryThresholdMs(long reliableAuditRecoveryThresholdMs) {
            this.reliableAuditRecoveryThresholdMs = reliableAuditRecoveryThresholdMs;
        }
        public DataSourceConfig getDatasource() { return datasource; }
        public void setDatasource(DataSourceConfig datasource) { this.datasource = datasource; }

        public static class DataSourceConfig {
            private String url;
            private String username;
            private String password;
            private String driverClassName;
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
            public String getUsername() { return username; }
            public void setUsername(String username) { this.username = username; }
            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
            public String getDriverClassName() { return driverClassName; }
            public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
        }
    }

    public static class AsyncConfig {
        private boolean enabled = true;
        private int logQueueCapacity = 1000;
        private int logBatchSize = 100;
        private long logFlushIntervalMs = 1000;
        private long shutdownFlushTimeoutMs = 10000;
        private int logWriteMaxRetries = 3;

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
        public int getLogWriteMaxRetries() { return logWriteMaxRetries; }
        public void setLogWriteMaxRetries(int logWriteMaxRetries) { this.logWriteMaxRetries = logWriteMaxRetries; }
    }

    public static class MonitoringConfig {
        private boolean enabled = true;
        private long slowTaskThresholdMs = 30000;
        private int consecutiveFailureThreshold = 3;
        private int maxMetricJobTags = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSlowTaskThresholdMs() { return slowTaskThresholdMs; }
        public void setSlowTaskThresholdMs(long slowTaskThresholdMs) { this.slowTaskThresholdMs = slowTaskThresholdMs; }
        public int getConsecutiveFailureThreshold() { return consecutiveFailureThreshold; }
        public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) { this.consecutiveFailureThreshold = consecutiveFailureThreshold; }
        public int getMaxMetricJobTags() { return maxMetricJobTags; }
        public void setMaxMetricJobTags(int maxMetricJobTags) { this.maxMetricJobTags = maxMetricJobTags; }
    }

    public static class TimeoutPoolConfig {
        private int coreSize = 2;
        private int maxSize = 5;
        private long shutdownAwaitMs = 10000;

        public int getCoreSize() { return coreSize; }
        public void setCoreSize(int coreSize) { this.coreSize = coreSize; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public long getShutdownAwaitMs() { return shutdownAwaitMs; }
        public void setShutdownAwaitMs(long shutdownAwaitMs) { this.shutdownAwaitMs = shutdownAwaitMs; }
    }

    public static class AnnotationConfig {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SchedulingConfig {
        private String defaultTimeZone = "UTC";

        public String getDefaultTimeZone() { return defaultTimeZone; }
        public void setDefaultTimeZone(String defaultTimeZone) { this.defaultTimeZone = defaultTimeZone; }
    }
}
