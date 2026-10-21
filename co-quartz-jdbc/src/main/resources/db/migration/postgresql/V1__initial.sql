-- New installations only. Existing legacy tables start with V2 after verifying their baseline.
CREATE TABLE quartz_task_log (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 job_key VARCHAR(200) NOT NULL,
 trigger_key VARCHAR(200) NOT NULL,
 start_time TIMESTAMP NOT NULL,
 end_time TIMESTAMP,
 execution_time_ms BIGINT,
 exec_state INT NOT NULL,
 error_message VARCHAR(500),
 stack_trace TEXT,
 execute_time TIMESTAMP NOT NULL
);
