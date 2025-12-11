-- 更新 quartz_task_log 表以支持更详细的日志记录
-- 添加 stack_trace 和 execution_time_ms 字段

-- MySQL
ALTER TABLE quartz_task_log ADD COLUMN IF NOT EXISTS stack_trace TEXT COMMENT '错误堆栈信息';
ALTER TABLE quartz_task_log ADD COLUMN IF NOT EXISTS execution_time_ms BIGINT COMMENT '任务执行时间(毫秒)';

-- PostgreSQL
-- ALTER TABLE quartz_task_log ADD COLUMN IF NOT EXISTS stack_trace TEXT;
-- ALTER TABLE quartz_task_log ADD COLUMN IF NOT EXISTS execution_time_ms BIGINT;