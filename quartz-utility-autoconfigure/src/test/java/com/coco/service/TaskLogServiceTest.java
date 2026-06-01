package com.coco.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coco.dto.PageResult;
import com.coco.dto.TaskExecutionLog;
import com.coco.dto.TaskLogQuery;
import com.coco.enums.LogTaskExecStateEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;

class TaskLogServiceTest {

    private TaskLogService taskLogService;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:task_log_service;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS quartz_task_log");
        jdbcTemplate.execute("""
                CREATE TABLE quartz_task_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    job_key VARCHAR(200) NOT NULL,
                    trigger_key VARCHAR(200) NOT NULL,
                    exec_state INT NOT NULL,
                    error_message TEXT,
                    stack_trace TEXT,
                    execution_time_ms BIGINT,
                    execute_time TIMESTAMP NOT NULL
                )
                """);
        taskLogService = new TaskLogService(jdbcTemplate);
    }

    @Test
    void pageLogsFiltersByJobAndState() {
        insert("jobA", LogTaskExecStateEnum.EXEC_SUCCESS.getCode());
        insert("jobA", LogTaskExecStateEnum.EXEC_FAIL.getCode());
        insert("jobB", LogTaskExecStateEnum.EXEC_FAIL.getCode());

        TaskLogQuery query = new TaskLogQuery();
        query.setJobKey("jobA");
        query.setExecState(LogTaskExecStateEnum.EXEC_FAIL.getCode());
        query.setPage(1);
        query.setSize(10);

        PageResult<TaskExecutionLog> result = taskLogService.pageLogs(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getJobKey()).isEqualTo("jobA");
        assertThat(taskLogService.statistics().getTotalExecutions()).isEqualTo(3);
        assertThat(taskLogService.statistics("jobA").getFailedExecutions()).isEqualTo(1);
    }

    private void insert(String jobKey, int state) {
        jdbcTemplate.update("INSERT INTO quartz_task_log "
                        + "(job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                jobKey, jobKey + "Trigger", state, null, null, 100L,
                new Timestamp(System.currentTimeMillis()));
    }
}
