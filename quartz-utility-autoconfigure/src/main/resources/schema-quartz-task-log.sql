-- quartz_task_log table schema.
-- Use the MySQL section directly, or copy the PostgreSQL section below.

-- MySQL 8+
CREATE TABLE IF NOT EXISTS quartz_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_key VARCHAR(200) NOT NULL,
    trigger_key VARCHAR(200) NOT NULL,
    exec_state INT NOT NULL COMMENT '0=FAIL, 1=SUCCESS',
    error_message TEXT,
    stack_trace TEXT,
    execution_time_ms BIGINT,
    execute_time TIMESTAMP NOT NULL,
    INDEX idx_job_key (job_key),
    INDEX idx_trigger_key (trigger_key),
    INDEX idx_execute_time (execute_time),
    INDEX idx_exec_state (exec_state),
    INDEX idx_job_execute_time (job_key, execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz task execution log';

-- PostgreSQL 12+
-- CREATE TABLE IF NOT EXISTS quartz_task_log (
--     id BIGSERIAL PRIMARY KEY,
--     job_key VARCHAR(200) NOT NULL,
--     trigger_key VARCHAR(200) NOT NULL,
--     exec_state INT NOT NULL,
--     error_message TEXT,
--     stack_trace TEXT,
--     execution_time_ms BIGINT,
--     execute_time TIMESTAMP NOT NULL,
-- );
-- CREATE INDEX IF NOT EXISTS idx_quartz_log_job_key ON quartz_task_log(job_key);
-- CREATE INDEX IF NOT EXISTS idx_quartz_log_trigger_key ON quartz_task_log(trigger_key);
-- CREATE INDEX IF NOT EXISTS idx_quartz_log_execute_time ON quartz_task_log(execute_time);
-- CREATE INDEX IF NOT EXISTS idx_quartz_log_exec_state ON quartz_task_log(exec_state);
-- CREATE INDEX IF NOT EXISTS idx_quartz_log_job_execute_time ON quartz_task_log(job_key, execute_time);
