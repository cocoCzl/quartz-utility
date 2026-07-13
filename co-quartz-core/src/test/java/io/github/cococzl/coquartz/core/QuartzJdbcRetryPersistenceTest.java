package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzJdbcRetryPersistenceTest {

    private static final String DATABASE_URL = "jdbc:h2:mem:retry-persistence;DB_CLOSE_DELAY=-1";

    private final List<Scheduler> schedulers = new CopyOnWriteArrayList<>();
    private final ExecutorService timeoutExecutor = Executors.newSingleThreadExecutor();
    private final RecordingLogService logs = new RecordingLogService();

    @BeforeEach
    void setUp() throws Exception {
        FailOnceJob.reset();
        logs.entries.clear();
        initialiseQuartzSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Scheduler scheduler : schedulers) {
            scheduler.shutdown(false);
        }
        timeoutExecutor.shutdownNow();
    }

    @Test
    void delayedRetrySurvivesSchedulerRestartAndKeepsItsExecutionCorrelation() throws Exception {
        Scheduler first = scheduler("first");
        QuartzTaskBuilder.newBuilder()
                .jobClass(FailOnceJob.class)
                .jobName("persistentRetry")
                .intervalInSeconds(3600)
                .startAt(new java.util.Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .retryTimes(1)
                .retryInterval(2_000)
                .concurrent(false)
                .schedule(first);
        first.start();
        first.triggerJob(JobKey.jobKey("persistentRetry", "DEFAULT"));
        assertThat(FailOnceJob.firstAttempt.await(5, TimeUnit.SECONDS)).isTrue();
        long retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean retryPersisted;
        do {
            retryPersisted = first.getTriggersOfJob(JobKey.jobKey("persistentRetry", "DEFAULT")).stream()
                    .anyMatch(trigger -> CoQuartzConstants.RETRY_TRIGGER_GROUP.equals(trigger.getKey().getGroup()));
            if (!retryPersisted) Thread.sleep(20);
        } while (!retryPersisted && System.nanoTime() < retryDeadline);
        assertThat(retryPersisted).isTrue();

        first.shutdown(false);
        schedulers.remove(first);

        Scheduler restarted = scheduler("restarted");
        restarted.start();
        assertThat(FailOnceJob.secondAttempt.await(8, TimeUnit.SECONDS)).isTrue();

        long logsDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (logs.entries.size() < 2 && System.nanoTime() < logsDeadline) Thread.sleep(20);

        assertThat(logs.entries).hasSize(2);
        assertThat(logs.entries).extracting(TaskExecutionLog::getExecutionId).doesNotContainNull();
        // Both attempts are part of one execution, so the single execution id must repeat.
        assertThat(logs.entries.get(0).getExecutionId()).isEqualTo(logs.entries.get(1).getExecutionId());
        assertThat(logs.entries).extracting(TaskExecutionLog::getAttempt).containsExactly(1, 2);
        assertThat(logs.entries).extracting(TaskExecutionLog::isFinalAttempt).containsExactly(false, true);
    }

    private Scheduler scheduler(String instanceId) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "retryPersistence");
        properties.setProperty("org.quartz.scheduler.instanceId", instanceId);
        properties.setProperty("org.quartz.threadPool.threadCount", "2");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        properties.setProperty("org.quartz.jobStore.dataSource", "retryDataSource");
        properties.setProperty("org.quartz.dataSource.retryDataSource.driver", "org.h2.Driver");
        properties.setProperty("org.quartz.dataSource.retryDataSource.URL", DATABASE_URL);
        properties.setProperty("org.quartz.dataSource.retryDataSource.maxConnections", "5");
        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        scheduler.setJobFactory(jobFactory());
        schedulers.add(scheduler);
        return scheduler;
    }

    private CoQuartzJobFactory jobFactory() {
        CoQuartzJobFactory factory = new CoQuartzJobFactory();
        factory.setAsyncTaskLogServiceProvider(provider(logs));
        factory.setTimeoutExecutor(timeoutExecutor);
        factory.setProperties(new CoQuartzProperties());
        return factory;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return value; }
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public java.util.Iterator<T> iterator() { return List.of(value).iterator(); }
        };
    }

    private static void initialiseQuartzSchema() throws Exception {
        String sql;
        try (var input = StdSchedulerFactory.class.getResourceAsStream(
                "/org/quartz/impl/jdbcjobstore/tables_h2.sql")) {
            if (input == null) throw new IOException("Quartz H2 schema resource is unavailable");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             Statement statement = connection.createStatement()) {
            for (String entry : sql.split(";")) {
                String trimmed = entry.replaceAll("(?m)^\\s*--.*$", "").trim();
                if (!trimmed.isEmpty()) statement.execute(trimmed);
            }
        }
    }

    static class FailOnceJob implements Job {
        static final AtomicInteger executions = new AtomicInteger();
        static CountDownLatch firstAttempt;
        static CountDownLatch secondAttempt;

        static void reset() {
            executions.set(0);
            firstAttempt = new CountDownLatch(1);
            secondAttempt = new CountDownLatch(1);
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            if (executions.incrementAndGet() == 1) {
                firstAttempt.countDown();
                throw new JobExecutionException("first attempt fails");
            }
            secondAttempt.countDown();
        }
    }

    static class RecordingLogService implements AsyncTaskLogService {
        final List<TaskExecutionLog> entries = new CopyOnWriteArrayList<>();
        @Override public void logTaskExecutionAsync(TaskExecutionLog log) { entries.add(log); }
        @Override public void flushLogsImmediately() { }
        @Override public void shutdown() { }
        @Override public int getQueueSize() { return 0; }
    }
}
