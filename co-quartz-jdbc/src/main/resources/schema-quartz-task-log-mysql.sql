CREATE TABLE IF NOT EXISTS quartz_task_log (
    id               VARCHAR(36)   NOT NULL,
    job_key          VARCHAR(200) NOT NULL,
    trigger_key      VARCHAR(200) NOT NULL,
    start_time       DATETIME     NOT NULL,
    end_time         DATETIME,
    execution_time_ms BIGINT,
    exec_state       INT          NOT NULL,
    error_message    VARCHAR(500),
    stack_trace      TEXT,
    attempt          INT          NOT NULL DEFAULT 1,
    is_final_attempt TINYINT(1)   NOT NULL DEFAULT 1,
    execute_time     DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_task_log_job_key_start_time ON quartz_task_log (job_key, start_time);
CREATE INDEX idx_task_log_exec_state ON quartz_task_log (exec_state);