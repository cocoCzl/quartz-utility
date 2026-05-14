-- quartz_task_log 建表脚本
-- 请根据实际数据库类型选择对应语法

-- MySQL
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
    INDEX idx_execute_time (execute_time),
    INDEX idx_exec_state (exec_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quartz task execution log';

-- PostgreSQL
-- CREATE TABLE IF NOT EXISTS quartz_task_log (
--     id BIGSERIAL PRIMARY KEY,
--     job_key VARCHAR(200) NOT NULL,
--     trigger_key VARCHAR(200) NOT NULL,
--     exec_state INT NOT NULL,
--     error_message TEXT,
--     stack_trace TEXT,
--     execution_time_ms BIGINT,
--     execute_time TIMESTAMP NOT NULL,
--     CREATE INDEX idx_quartz_log_job_key ON quartz_task_log(job_key);
--     CREATE INDEX idx_quartz_log_execute_time ON quartz_task_log(execute_time);
--     CREATE INDEX idx_quartz_log_exec_state ON quartz_task_log(exec_state);
-- );