-- Run once under a single migration owner; nullable legacy ownership is never auto-recovered.
ALTER TABLE quartz_task_log ADD COLUMN scheduler_name VARCHAR(200);
ALTER TABLE quartz_task_log ADD COLUMN application_namespace VARCHAR(200);
ALTER TABLE quartz_task_log ADD COLUMN origin_trigger_key VARCHAR(400);
ALTER TABLE quartz_task_log ADD COLUMN origin_scheduled_time BIGINT;
CREATE INDEX idx_task_log_cleanup ON quartz_task_log (exec_state, execute_time, id);
