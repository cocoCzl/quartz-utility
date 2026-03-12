package com.coco.core;

import com.coco.core.QuartzComponent.Builder;
import com.coco.exception.QuartzUtilityException;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Quartz 调度器封装类
 * 提供了对 Quartz 原生调度器的增强功能，包括任务调度、管理、监控等
 */
public record CoQuartzScheduler(Scheduler scheduler) {

    /**
     * 构造函数
     *
     * @param scheduler Quartz 原生调度器实例
     */
    public CoQuartzScheduler {
    }

    // ========================================
    // 任务调度方法
    // ========================================

    /**
     * 安排一个简单间隔的任务，使用默认的 JobKey 和 TriggerKey，并携带任务数据。
     *
     * @param jobClass        实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param jobDataMap      任务数据映射，包含任务执行时需要使用的参数。可以为 null。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位、Cron表达式等。
     * @throws SchedulerException 如果在调度任务过程中出现异常。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass, JobDataMap jobDataMap,
            QuartzComponent quartzComponent) throws SchedulerException {
        JobKey jobKey = SchedulerCore.getDefaultJobKey();
        TriggerKey triggerKey = SchedulerCore.getDefaultTriggerKey();
        scheduleSimpleIntervalJob(jobClass, jobKey, triggerKey, jobDataMap, null, quartzComponent);
    }

    /**
     * 安排一个简单间隔的任务，使用默认的 JobKey 和 TriggerKey，不携带任务数据。
     *
     * @param jobClass        实现了 Job 接口的任务类。
     * @param quartzComponent 包含任务调度相关配置信息的组件。
     * @throws SchedulerException 如果在调度任务过程中出现异常。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass,
            QuartzComponent quartzComponent) throws SchedulerException {
        JobKey jobKey = SchedulerCore.getDefaultJobKey();
        TriggerKey triggerKey = SchedulerCore.getDefaultTriggerKey();
        scheduleSimpleIntervalJob(jobClass, jobKey, triggerKey, null, null, quartzComponent);
    }

    /**
     * 安排一个简单间隔的任务，可自定义 JobKey、TriggerKey，可携带任务数据和任务监听器。
     * 该方法会检查是否存在已调度的任务，如果存在且执行间隔不同，会删除现有任务。
     *
     * @param jobClass        实现了 Job 接口的任务类。
     * @param jobKey          任务的唯一标识。
     * @param triggerKey      触发器的唯一标识。
     * @param jobDataMap      任务数据映射。可以为 null。
     * @param jobListener     任务监听器。可以为 null。
     * @param quartzComponent 包含任务调度相关配置信息的组件。
     * @throws SchedulerException 如果在调度任务过程中出现异常。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass, JobKey jobKey,
            TriggerKey triggerKey, JobDataMap jobDataMap, JobListener jobListener,
            QuartzComponent quartzComponent) throws SchedulerException {

        JobDetail jobDetail = SchedulerCore.getJobDetail(jobClass, jobKey, jobDataMap,
                quartzComponent);

        // 如果配置为使用 Cron 触发器，则按 Cron 触发器处理
        if (quartzComponent.isUseCronTrigger()) {
            scheduleCronJob(jobClass, jobKey, triggerKey, jobDataMap, jobListener, quartzComponent);
            return;
        }

        Trigger existingTrigger = scheduler.getTrigger(triggerKey);

        // 检查是否存在已调度的任务
        if (existingTrigger != null) {
            // 获取执行间隔时间（单位毫秒）
            int currentInterval = (int) ((SimpleTrigger) existingTrigger).getRepeatInterval();
            int newInterval = getInterval(quartzComponent);
            
            // 如果间隔相同，任务已存在且配置匹配，无需重新调度
            if (currentInterval == newInterval) {
                return;
            }
            
            // 如果间隔不同，删除现有任务以便重新调度
            scheduler.pauseTrigger(triggerKey);
            scheduler.unscheduleJob(triggerKey);
            scheduler.deleteJob(jobDetail.getKey());
        }

        // 将任务和触发器注册到Scheduler中
        Trigger trigger = createSimpleTrigger(triggerKey, quartzComponent);
        
        if (jobListener != null) {
            scheduler.getListenerManager().addJobListener(jobListener);
        }
        scheduler.scheduleJob(jobDetail, trigger);
    }

    /**
     * 安排一个 Cron 任务
     *
     * @param jobClass        任务类，必须实现 org.quartz.Job 接口
     * @param jobKey          任务键，用于唯一标识一个任务
     * @param triggerKey      触发器键，用于唯一标识一个触发器
     * @param jobDataMap      任务数据映射，用于传递任务执行时所需的参数
     * @param jobListener     任务监听器，用于监听任务执行过程中的事件
     * @param quartzComponent 任务配置组件，包含任务的各种配置选项
     * @throws SchedulerException 调度器异常
     */
    public void scheduleCronJob(Class<? extends Job> jobClass, JobKey jobKey,
            TriggerKey triggerKey, JobDataMap jobDataMap, JobListener jobListener,
            QuartzComponent quartzComponent) throws SchedulerException {

        if (!quartzComponent.isUseCronTrigger() || quartzComponent.getCronExpression() == null) {
            throw new QuartzUtilityException("Cron expression is required for cron job",
                    QuartzUtilityException.PARAMETER_ABNORMAL);
        }

        JobDetail jobDetail = SchedulerCore.getJobDetail(jobClass, jobKey, jobDataMap,
                quartzComponent);

        Trigger existingTrigger = scheduler.getTrigger(triggerKey);
        if (existingTrigger != null) {
            // 检查 Cron 表达式是否相同
            if (existingTrigger instanceof CronTrigger) {
                String existingCron = ((CronTrigger) existingTrigger).getCronExpression();
                if (existingCron.equals(quartzComponent.getCronExpression())) {
                    // Cron 表达式相同，无需重新调度
                    return;
                }
            }
            
            // Cron 表达式不同，删除现有任务
            scheduler.pauseTrigger(triggerKey);
            scheduler.unscheduleJob(triggerKey);
            scheduler.deleteJob(jobDetail.getKey());
        }

        // 创建新的 Cron 触发器
        Trigger trigger = SchedulerCore.getCronTrigger(triggerKey, quartzComponent.getCronExpression());

        if (jobListener != null) {
            scheduler.getListenerManager().addJobListener(jobListener);
        }
        scheduler.scheduleJob(jobDetail, trigger);
    }

    /**
     * 安排一个 Cron 任务，使用默认的键和配置
     *
     * @param jobClass        任务类，必须实现 org.quartz.Job 接口
     * @param cronExpression  Cron 表达式，定义任务的执行时间规则
     * @param quartzComponent 任务配置组件，包含任务的各种配置选项
     * @throws SchedulerException 调度器异常
     */
    public void scheduleCronJob(Class<? extends Job> jobClass, String cronExpression,
            QuartzComponent quartzComponent) throws SchedulerException {
        JobKey jobKey = SchedulerCore.getDefaultJobKey();
        TriggerKey triggerKey = SchedulerCore.getDefaultTriggerKey();

        // 在 QuartzComponent 中设置 Cron 表达式
        QuartzComponent updatedComponent = new Builder()
                .setCronExpression(cronExpression)
                .setDescription(quartzComponent.getDescription())
                .setShouldRecover(quartzComponent.isShouldRecover())
                .setDurability(quartzComponent.isDurability())
                .setMisfireInstruction(quartzComponent.getMisfireInstruction())
                .build();

        scheduleCronJob(jobClass, jobKey, triggerKey, null, null, updatedComponent);
    }

    // ========================================
    // 任务管理方法
    // ========================================

    /**
     * 立即触发一次任务执行
     *
     * @param jobKey 任务键
     * @throws SchedulerException 调度器异常
     */
    public void triggerJob(JobKey jobKey) throws SchedulerException {
        scheduler.triggerJob(jobKey);
    }

    /**
     * 立即触发一次任务执行（带参数）
     *
     * @param jobKey     任务键
     * @param jobDataMap 任务数据
     * @throws SchedulerException 调度器异常
     */
    public void triggerJob(JobKey jobKey, JobDataMap jobDataMap) throws SchedulerException {
        scheduler.triggerJob(jobKey, jobDataMap);
    }

    /**
     * 暂停指定的任务
     *
     * @param jobKey 任务键
     * @throws SchedulerException 调度器异常
     */
    public void pauseJob(JobKey jobKey) throws SchedulerException {
        scheduler.pauseJob(jobKey);
    }

    /**
     * 恢复指定的任务
     *
     * @param jobKey 任务键
     * @throws SchedulerException 调度器异常
     */
    public void resumeJob(JobKey jobKey) throws SchedulerException {
        scheduler.resumeJob(jobKey);
    }

    /**
     * 暂停指定的触发器
     *
     * @param triggerKey 触发器键
     * @throws SchedulerException 调度器异常
     */
    public void pauseTrigger(TriggerKey triggerKey) throws SchedulerException {
        scheduler.pauseTrigger(triggerKey);
    }

    /**
     * 恢复指定的触发器
     *
     * @param triggerKey 触发器键
     * @throws SchedulerException 调度器异常
     */
    public void resumeTrigger(TriggerKey triggerKey) throws SchedulerException {
        scheduler.resumeTrigger(triggerKey);
    }

    /**
     * 删除指定的任务
     *
     * @param jobKey 任务键
     * @return 删除是否成功
     * @throws SchedulerException 调度器异常
     */
    public boolean deleteJob(JobKey jobKey) throws SchedulerException {
        return scheduler.deleteJob(jobKey);
    }

    /**
     * 批量删除任务
     *
     * @param jobKeys 任务键列表
     * @return 删除是否成功
     * @throws SchedulerException 调度器异常
     */
    public boolean deleteJobs(List<JobKey> jobKeys) throws SchedulerException {
        return scheduler.deleteJobs(jobKeys);
    }

    // ========================================
    // 任务查询方法
    // ========================================

    /**
     * 检查任务是否存在
     *
     * @param jobKey 任务键
     * @return 如果任务存在返回 true，否则返回 false
     * @throws SchedulerException 调度器异常
     */
    public boolean checkExists(JobKey jobKey) throws SchedulerException {
        return scheduler.checkExists(jobKey);
    }

    /**
     * 获取任务详情
     *
     * @param jobKey 任务键
     * @return 任务详情，不存在返回 null
     * @throws SchedulerException 调度器异常
     */
    public JobDetail getJobDetail(JobKey jobKey) throws SchedulerException {
        return scheduler.getJobDetail(jobKey);
    }

    /**
     * 获取任务的触发器
     *
     * @param jobKey 任务键
     * @return 触发器列表
     * @throws SchedulerException 调度器异常
     */
    public List<? extends Trigger> getTriggersOfJob(JobKey jobKey) throws SchedulerException {
        return scheduler.getTriggersOfJob(jobKey);
    }

    /**
     * 获取触发器
     *
     * @param triggerKey 触发器键
     * @return 触发器，不存在返回 null
     * @throws SchedulerException 调度器异常
     */
    public Trigger getTrigger(TriggerKey triggerKey) throws SchedulerException {
        return scheduler.getTrigger(triggerKey);
    }

    /**
     * 获取触发器的状态
     *
     * @param triggerKey 触发器键
     * @return 触发器状态
     * @throws SchedulerException 调度器异常
     */
    public Trigger.TriggerState getTriggerState(TriggerKey triggerKey) throws SchedulerException {
        return scheduler.getTriggerState(triggerKey);
    }

    /**
     * 获取所有任务组名
     *
     * @return 任务组名列表
     * @throws SchedulerException 调度器异常
     */
    public List<String> getJobGroupNames() throws SchedulerException {
        return scheduler.getJobGroupNames();
    }

    /**
     * 获取指定组中的所有任务键
     *
     * @param groupName 组名
     * @return 任务键集合
     * @throws SchedulerException 调度器异常
     */
    public Set<JobKey> getJobKeys(String groupName) throws SchedulerException {
        return scheduler.getJobKeys(GroupMatcher.groupEquals(groupName));
    }

    /**
     * 获取所有任务键
     *
     * @return 任务键列表
     * @throws SchedulerException 调度器异常
     */
    public List<JobKey> getAllJobKeys() throws SchedulerException {
        List<JobKey> allJobKeys = new ArrayList<>();
        for (String groupName : scheduler.getJobGroupNames()) {
            allJobKeys.addAll(scheduler.getJobKeys(GroupMatcher.groupEquals(groupName)));
        }
        return allJobKeys;
    }

    /**
     * 获取下一次触发时间
     *
     * @param triggerKey 触发器键
     * @return 下一次触发时间，如果触发器不存在返回 null
     * @throws SchedulerException 调度器异常
     */
    public Date getNextFireTime(TriggerKey triggerKey) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(triggerKey);
        return trigger != null ? trigger.getNextFireTime() : null;
    }

    /**
     * 获取上一次触发时间
     *
     * @param triggerKey 触发器键
     * @return 上一次触发时间，如果触发器不存在返回 null
     * @throws SchedulerException 调度器异常
     */
    public Date getPreviousFireTime(TriggerKey triggerKey) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(triggerKey);
        return trigger != null ? trigger.getPreviousFireTime() : null;
    }

    // ========================================
    // 辅助方法
    // ========================================

    /**
     * 获取触发器键，使用默认组名和指定任务名
     *
     * @param taskName 任务名称
     * @return 触发器键
     */
    public TriggerKey getTriggerKey(String taskName) {
        return SchedulerCore.getTriggerKey(taskName);
    }

    /**
     * 获取触发器键，使用指定的名称和组名
     *
     * @param name  触发器名称
     * @param group 触发器所属组名
     * @return 触发器键
     */
    public TriggerKey getTriggerKey(String name, String group) {
        return SchedulerCore.getTriggerKey(name, group);
    }

    /**
     * 获取默认的触发器键
     *
     * @return 默认触发器键
     */
    public TriggerKey getDefaultTriggerKey() {
        return SchedulerCore.getDefaultTriggerKey();
    }

    /**
     * 获取任务键，使用默认组名和指定任务名
     *
     * @param taskName 任务名称
     * @return 任务键
     */
    public JobKey getJobKey(String taskName) {
        return SchedulerCore.getJobKey(taskName);
    }

    /**
     * 获取任务键，使用指定的名称和组名
     *
     * @param name  任务名称
     * @param group 任务所属组名
     * @return 任务键
     */
    public JobKey getJobKey(String name, String group) {
        return SchedulerCore.getJobKey(name, group);
    }

    /**
     * 获取默认的任务键
     *
     * @return 默认任务键
     */
    public JobKey getDefaultJobKey() {
        return SchedulerCore.getDefaultJobKey();
    }

    /**
     * 获取底层的 Scheduler 对象
     *
     * @return 底层的 Quartz Scheduler 实例
     */
    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    // ========================================
    // 私有方法
    // ========================================

    /**
     * 创建简单触发器
     */
    private Trigger createSimpleTrigger(TriggerKey triggerKey, QuartzComponent quartzComponent) {
        return switch (quartzComponent.getTimeEnum()) {
            case HOURS -> SchedulerCore.getHoursSimpleTrigger(triggerKey,
                    quartzComponent.getTimeInterval(), quartzComponent.getMisfireInstruction());
            case MINUTES -> SchedulerCore.getMinuteSimpleTrigger(triggerKey,
                    quartzComponent.getTimeInterval(), quartzComponent.getMisfireInstruction());
            case SECONDS -> SchedulerCore.getSecondsSimpleTrigger(triggerKey,
                    quartzComponent.getTimeInterval(), quartzComponent.getMisfireInstruction());
            default -> throw new QuartzUtilityException("The interval type is abnormal",
                    QuartzUtilityException.PARAMETER_ABNORMAL);
        };
    }

    /**
     * 根据 QuartzComponent 计算时间间隔（毫秒）
     *
     * @param quartzComponent 任务配置组件
     * @return 时间间隔（毫秒）
     */
    private int getInterval(QuartzComponent quartzComponent) {
        return switch (quartzComponent.getTimeEnum()) {
            case HOURS -> quartzComponent.getTimeInterval() * 60 * 60 * 1000;
            case MINUTES -> quartzComponent.getTimeInterval() * 60 * 1000;
            case SECONDS -> quartzComponent.getTimeInterval() * 1000;
            default -> throw new QuartzUtilityException("The interval type is abnormal",
                    QuartzUtilityException.PARAMETER_ABNORMAL);
        };
    }
}