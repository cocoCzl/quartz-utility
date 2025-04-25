package com.coco.core;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public abstract class BaseAbstractQuartzJob implements Job {

    @Autowired
    @Qualifier("quartzJdbcTemplate")
    private JdbcTemplate quartzJdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 初始化 Java 标准库的日志记录器
    private static final Logger logger = Logger.getLogger(BaseAbstractQuartzJob.class.getName());

    /**
     * 抽象方法，子类需要实现具体的任务执行逻辑
     *
     * @param context 任务执行上下文
     * @throws Throwable 可能抛出的异常
     */
    protected abstract void executeQuartzTask(JobExecutionContext context) throws Throwable;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        TransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);

        byte execState = LogTaskExecStateEnum.EXEC_SUCCESS.getCode();
        String errorMessage = null;
        try {
            // 调用抽象方法执行具体任务
            executeQuartzTask(context);
        } catch (Throwable e) {
            execState = LogTaskExecStateEnum.EXEC_FAIL.getCode();
            errorMessage = e.getMessage();
            throw new JobExecutionException(e);
        } finally {
            // 记录日志
            try {
                String jobKey = context.getJobDetail().getKey().toString();
                String triggerKey = context.getTrigger().getKey().toString();

                Optional<Integer> optional = checkExists(jobKey, triggerKey);
                if (optional.isPresent()) {
                    // 更新LOG数据
                    int update = updateTaskLog(execState, errorMessage, optional.get());
                    if (update != 1) {
                        throw new SQLException("update log error.");
                    }
                } else {
                    // 插入LOG数据
                    int insert = insertTaskLog(jobKey, triggerKey, execState, errorMessage);
                    if (insert != 1) {
                        throw new SQLException("insert log error.");
                    }
                }
                // 提交事务
                transactionManager.commit(status);
            } catch (Exception e) {
                // 回滚
                transactionManager.rollback(status);
                logger.log(Level.SEVERE, "Transaction rolled back due to an error: " + e.getMessage(), e);
            }
        }
    }

    private Optional<Integer> checkExists(String jobKey, String triggerKey) {
        return quartzJdbcTemplate.query(SELECT_SQL, (rs, rowNum) -> rs.getInt("id"), jobKey,
                triggerKey).stream().findFirst();
    }

    /**
     * 根据任务日志的 ID 更新执行状态、错误信息和执行时间
     *
     * @param id            任务日志的 ID
     * @param execState     执行状态，0 失败，1 成功
     * @param errorMessage  错误信息
     * @return 更新的记录数
     */
    private int updateTaskLog(byte execState, String errorMessage, int id) {
        return quartzJdbcTemplate.update(UPDATE_SQL, execState, errorMessage,
                new Timestamp(System.currentTimeMillis()), id);
    }

    /**
     * 插入任务日志记录
     *
     * @param jobKey        job 标识
     * @param triggerKey    trigger 标识
     * @param execState     执行状态（0 失败，1 成功）
     * @param errorMessage  错误信息（可为 null）
     * @return 插入操作影响的行数
     */
    public int insertTaskLog(String jobKey, String triggerKey, int execState, String errorMessage) {
        return quartzJdbcTemplate.update(INSERT_SQL, jobKey, triggerKey, execState, errorMessage,
                new Timestamp(System.currentTimeMillis()));
    }

    private static final String INSERT_SQL = "INSERT INTO quartz_task_log (job_key, trigger_key, exec_state, error_message, execute_time) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE quartz_task_log SET exec_state = ?, error_message = ?, execute_time = ? WHERE id = ?";
    private static final String SELECT_SQL = "SELECT id FROM quartz_task_log WHERE job_key = ? AND trigger_key = ?";

}
