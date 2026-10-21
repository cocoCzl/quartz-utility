-- Run exactly once after the legacy baseline/V1. No data deletion.
ALTER TABLE quartz_task_log ADD COLUMN attempt INT NOT NULL DEFAULT 1;
ALTER TABLE quartz_task_log ADD COLUMN is_final_attempt BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quartz_task_log ADD COLUMN execution_id VARCHAR(36);
ALTER TABLE quartz_task_log ADD COLUMN fire_instance_id VARCHAR(200);
ALTER TABLE quartz_task_log ADD COLUMN scheduler_instance_id VARCHAR(200);
ALTER TABLE quartz_task_log ADD COLUMN definition_version VARCHAR(100);
CREATE INDEX idx_task_log_job_key_start_time ON quartz_task_log (job_key, start_time);
CREATE INDEX idx_task_log_exec_state ON quartz_task_log (exec_state);
CREATE INDEX idx_task_log_execution_id ON quartz_task_log (execution_id);
