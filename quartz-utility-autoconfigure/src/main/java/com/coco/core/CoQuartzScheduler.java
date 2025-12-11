package com.coco.core;

import com.coco.core.QuartzComponent.Builder;
import com.coco.exception.QuartzUtilityException;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

/**
 * Quartz 调度器封装类 提供了对 Quartz 原生调度器的增强功能，包括任务调度、管理、监控等
 */
public record CoQuartzScheduler(Scheduler scheduler) {

    /**
     * 构造函数
     *
     * @param scheduler Quartz 原生调度器实例
     */
    public CoQuartzScheduler {
    }

    /**
     * 安排一个简单间隔的任务，使用默认的 JobKey 和 TriggerKey，并携带任务数据。 此方法会调用另一个重载的 scheduleSimpleIntervalJob
     * 方法，将任务类、默认的 JobKey、TriggerKey、任务数据和 Quartz 组件信息传递过去。
     *
     * @param jobClass        实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param jobDataMap      任务数据映射，包含任务执行时需要使用的参数。可以为 null。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位、Cron表达式等。
     * @throws SchedulerException 如果在调度任务过程中出现异常，如调度器未启动、任务或触发器注册失败等。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass, JobDataMap jobDataMap,
            QuartzComponent quartzComponent) throws SchedulerException {
        JobKey jobKey = SchedulerCore.getDefaultJobKey();
        TriggerKey triggerKey = SchedulerCore.getDefaultTriggerKey();
        scheduleSimpleIntervalJob(jobClass, jobKey, triggerKey, jobDataMap, null, quartzComponent);
    }

    /**
     * 安排一个简单间隔的任务，使用默认的 JobKey 和 TriggerKey，不携带任务数据。 此方法会调用另一个重载的 scheduleSimpleIntervalJob
     * 方法，将任务类、默认的 JobKey、TriggerKey 和 Quartz 组件信息传递过去。
     *
     * @param jobClass        实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位、Cron表达式等。
     * @throws SchedulerException 如果在调度任务过程中出现异常，如调度器未启动、任务或触发器注册失败等。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass,
            QuartzComponent quartzComponent) throws SchedulerException {
        JobKey jobKey = SchedulerCore.getDefaultJobKey();
        TriggerKey triggerKey = SchedulerCore.getDefaultTriggerKey();
        scheduleSimpleIntervalJob(jobClass, jobKey, triggerKey, null, null, quartzComponent);
    }

    /**
     * 安排一个简单间隔的任务，可自定义 JobKey、TriggerKey，可携带任务数据和任务监听器。 该方法会检查是否存在已调度的任务，如果存在且执行间隔不同，会删除现有任务。
     * 若任务不存在，则根据 Quartz 组件中的时间枚举创建相应的触发器，并将任务和触发器注册到调度器中。
     *
     * @param jobClass        实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param jobKey          任务的唯一标识，用于在调度器中区分不同的任务。
     * @param triggerKey      触发器的唯一标识，用于在调度器中区分不同的触发器。
     * @param jobDataMap      任务数据映射，包含任务执行时需要使用的参数。可以为 null。
     * @param jobListener     任务监听器，用于监听任务的执行状态，如任务开始、结束等。可以为 null。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位、Cron表达式等。
     * @throws SchedulerException 如果在调度任务过程中出现异常，如调度器未启动、任务或触发器注册失败等。
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

        // 是否存在已调度的任务
        if (existingTrigger != null) {
            // 获取执行间隔时间（单位毫秒）
            int currentInterval = (int) ((SimpleTrigger) existingTrigger).getRepeatInterval();
            int newInterval = getInterval(quartzComponent);
            // 如果间隔不同，删除现有任务
            if (currentInterval != newInterval) {
                scheduler.pauseTrigger(triggerKey);
                scheduler.unscheduleJob(triggerKey);
                scheduler.deleteJob(jobDetail.getKey());
            }
        }

        // 将任务和触发器注册到Scheduler中
        if (!scheduler.checkExists(jobDetail.getKey())) {
            Trigger trigger;
            switch (quartzComponent.getTimeEnum()) {
                case HOURS -> trigger = SchedulerCore.getHoursSimpleTrigger(triggerKey,
                        quartzComponent.getTimeInterval(), quartzComponent.getMisfireInstruction());
                case MINUTES -> trigger = SchedulerCore.getMinuteSimpleTrigger(triggerKey,
                        quartzComponent.getTimeInterval(), quartzComponent.getMisfireInstruction());
                case SECONDS -> trigger = SchedulerCore.getSecondsSimpleTrigger(triggerKey,
                        quartzComponent.getTimeInterval(), quartzComponent.getMisfireInstruction());
                default -> throw new QuartzUtilityException("The interval type is abnormal",
                        QuartzUtilityException.PARAMETER_ABNORMAL);
            }
            if (jobListener != null) {
                scheduler.getListenerManager().addJobListener(jobListener);
            }
            scheduler.scheduleJob(jobDetail, trigger);
        }
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
            scheduler.pauseTrigger(triggerKey);
            scheduler.unscheduleJob(triggerKey);
            scheduler.deleteJob(jobDetail.getKey());
        }

        if (!scheduler.checkExists(jobDetail.getKey())) {
            Trigger trigger = SchedulerCore.getCronTrigger(triggerKey,
                    quartzComponent.getCronExpression());

            if (jobListener != null) {
                scheduler.getListenerManager().addJobListener(jobListener);
            }
            scheduler.scheduleJob(jobDetail, trigger);
        }
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
        // 由于 QuartzComponent 是不可变的，我们需要通过 builder 重新创建
        QuartzComponent updatedComponent = new Builder()
                .setCronExpression(cronExpression)
                .setDescription(quartzComponent.getDescription())
                .setShouldRecover(quartzComponent.isShouldRecover())
                .setDurability(quartzComponent.isDurability())
                .setMisfireInstruction(quartzComponent.getMisfireInstruction())
                .build();

        scheduleCronJob(jobClass, jobKey, triggerKey, null, null, updatedComponent);
    }

    /**
     * 根据 QuartzComponent 计算时间间隔（毫秒）
     *
     * @param quartzComponent 任务配置组件
     * @return 时间间隔（毫秒）
     * @throws QuartzUtilityException 当时间单位异常时抛出
     */
    private int getInterval(QuartzComponent quartzComponent) {
        int newInterval;
        switch (quartzComponent.getTimeEnum()) {
            case HOURS -> newInterval = quartzComponent.getTimeInterval() * 60 * 60 * 1000;
            case MINUTES -> newInterval = quartzComponent.getTimeInterval() * 60 * 1000;
            case SECONDS -> newInterval = quartzComponent.getTimeInterval() * 1000;
            default -> throw new QuartzUtilityException("The interval type is abnormal",
                    QuartzUtilityException.PARAMETER_ABNORMAL);
        }
        return newInterval;
    }

    /**
     * 暂停指定的任务
     *
     * @param jobKey 任务键，用于标识要暂停的任务
     * @throws SchedulerException 调度器异常
     */
    public void pauseJob(JobKey jobKey) throws SchedulerException {
        scheduler.pauseJob(jobKey);
    }

    /**
     * 恢复指定的任务
     *
     * @param jobKey 任务键，用于标识要恢复的任务
     * @throws SchedulerException 调度器异常
     */
    public void resumeJob(JobKey jobKey) throws SchedulerException {
        scheduler.resumeJob(jobKey);
    }

    /**
     * 删除指定的任务
     *
     * @param jobKey 任务键，用于标识要删除的任务
     * @return 删除是否成功
     * @throws SchedulerException 调度器异常
     */
    public boolean deleteJob(JobKey jobKey) throws SchedulerException {
        return scheduler.deleteJob(jobKey);
    }

    /**
     * 检查任务是否存在
     *
     * @param jobKey 任务键，用于标识要检查的任务
     * @return 如果任务存在返回 true，否则返回 false
     * @throws SchedulerException 调度器异常
     */
    public boolean checkExists(JobKey jobKey) throws SchedulerException {
        return scheduler.checkExists(jobKey);
    }

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
}
