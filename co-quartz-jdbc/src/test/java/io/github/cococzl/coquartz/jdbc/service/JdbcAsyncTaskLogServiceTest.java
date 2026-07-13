package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.TaskLogPipelineEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAsyncTaskLogServiceTest {

    private JdbcAsyncTaskLogService asyncLogService;
    private JdbcTaskLogRepository repository;
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        asyncLogService.shutdown();
    }

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_" + System.nanoTime())
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setAutoCreateTable(true);
        SchemaInitializer schemaInitializer = new SchemaInitializer(jdbcTemplate, properties);
        schemaInitializer.initialize();
        repository = new JdbcTaskLogRepository(jdbcTemplate);
        asyncLogService = new JdbcAsyncTaskLogService(repository, 500, 50, 100);
    }

    private TaskExecutionLog createLog(String jobKey) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(UUID.randomUUID().toString());
        log.setJobKey(jobKey);
        log.setTriggerKey("DEFAULT.TRIGGER");
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());
        log.setExecutionTimeMs(50L);
        log.setExecState(LogTaskExecStateEnum.SUCCESS);
        log.setAttempt(1);
        log.setFinalAttempt(true);
        log.setExecuteTime(LocalDateTime.now());
        return log;
    }

    @Test
    void logTaskExecutionAsync_flushesToRepository() throws Exception {
        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.asyncJob"));
        asyncLogService.flushLogsImmediately();
        Thread.sleep(200);

        List<TaskExecutionLog> logs = repository.latestLogs("DEFAULT.asyncJob", 10);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getJobKey()).isEqualTo("DEFAULT.asyncJob");
    }

    @Test
    void logTaskExecutionAsync_multipleLogs() throws Exception {
        for (int i = 0; i < 5; i++) {
            asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.batchJob"));
        }
        asyncLogService.flushLogsImmediately();
        Thread.sleep(200);

        List<TaskExecutionLog> logs = repository.latestLogs("DEFAULT.batchJob", 10);
        assertThat(logs).hasSize(5);
    }

    @Test
    void getQueueSize_returnsCorrectSize() {
        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.queuedJob"));
        assertThat(asyncLogService.getQueueSize()).isEqualTo(1);
        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.queuedJob"));
        assertThat(asyncLogService.getQueueSize()).isEqualTo(2);
    }

    @Test
    void shutdown_flushesRemainingLogs() throws Exception {
        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.shutdownJob"));
        asyncLogService.shutdown();
        Thread.sleep(200);

        List<TaskExecutionLog> logs = repository.latestLogs("DEFAULT.shutdownJob", 10);
        assertThat(logs).hasSize(1);
    }

    @Test
    void fullQueueIncrementsDroppedCountAndPublishesPipelineEvent() {
        asyncLogService.shutdown();
        List<TaskLogPipelineEvent> events = new CopyOnWriteArrayList<>();
        asyncLogService = new JdbcAsyncTaskLogService(repository, 1, 10, 60_000,
                1_000, 1, event -> { if (event instanceof TaskLogPipelineEvent pipelineEvent) events.add(pipelineEvent); });

        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.first"));
        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.dropped"));

        assertThat(asyncLogService.getPipelineStatus().queueSize()).isEqualTo(1);
        assertThat(asyncLogService.getPipelineStatus().droppedCount()).isEqualTo(1);
        assertThat(events).anyMatch(event -> event.getType() == TaskLogPipelineEvent.Type.QUEUE_FULL);
    }

    @Test
    void permanentWriteFailureIsBoundedAndObservable() {
        asyncLogService.shutdown();
        List<TaskLogPipelineEvent> events = new CopyOnWriteArrayList<>();
        asyncLogService = new JdbcAsyncTaskLogService(repository, 10, 10, 60_000,
                1_000, 1, event -> { if (event instanceof TaskLogPipelineEvent pipelineEvent) events.add(pipelineEvent); });
        jdbcTemplate.execute("DROP TABLE quartz_task_log");

        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.writeFailure"));
        asyncLogService.flushLogsImmediately();
        asyncLogService.flushLogsImmediately();

        assertThat(asyncLogService.getPipelineStatus().writeFailureCount()).isEqualTo(2);
        assertThat(asyncLogService.getPipelineStatus().permanentFailureCount()).isEqualTo(1);
        assertThat(asyncLogService.getPipelineStatus().queueSize()).isZero();
        assertThat(events).anyMatch(event -> event.getType() == TaskLogPipelineEvent.Type.PERMANENT_WRITE_FAILURE);
    }

    @Test
    void shutdownReportsRecordsThatCouldNotBeFlushedWithinConfiguredTimeout() {
        asyncLogService.shutdown();
        List<TaskLogPipelineEvent> events = new CopyOnWriteArrayList<>();
        asyncLogService = new JdbcAsyncTaskLogService(repository, 10, 10, 60_000,
                0, 10, event -> { if (event instanceof TaskLogPipelineEvent pipelineEvent) events.add(pipelineEvent); });
        jdbcTemplate.execute("DROP TABLE quartz_task_log");

        asyncLogService.logTaskExecutionAsync(createLog("DEFAULT.unflushed"));
        asyncLogService.shutdown();

        assertThat(asyncLogService.getPipelineStatus().unflushedCount()).isEqualTo(1);
        assertThat(events).anyMatch(event -> event.getType() == TaskLogPipelineEvent.Type.SHUTDOWN_UNFLUSHED);
    }
}
