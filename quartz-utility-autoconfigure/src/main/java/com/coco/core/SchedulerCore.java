package com.coco.core;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

public class SchedulerCore {

    /**
     * 该工具类方法用于获取一个预初始化的 JobBuilder 对象，
     *
     * @param jobClass 实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @return 返回一个已初始化的 JobBuilder 对象，其任务类已设置为传入的 jobClass。
     */
    public static JobBuilder getJobBuilder(Class<? extends Job> jobClass) {
        return JobBuilder.newJob(jobClass);
    }

    /**
     * 获取 JobDetail 的静态方法。
     * JobDetail 是 Quartz 框架中用于定义任务的详细信息的类，包括任务的类、标识、描述、是否请求恢复、是否持久化存储等。
     *
     * @param jobClass     实现了 Job 接口的任务类，用于指定要执行的具体任务逻辑。
     * @param jobKey       JobKey 对象，用于唯一标识一个任务，包含任务的名称和所属组。
     * @param dataMap      JobDataMap 对象，用于存储任务执行时所需的参数数据。可以为 null。
     * @param quartzComponent QuartzComponent 对象，包含任务的一些额外配置选项，如描述和是否请求恢复。
     * @return 返回一个构建好的 JobDetail 对象，用于后续与 Trigger 关联以调度任务。
     */
    public static JobDetail getJobDetail(Class<? extends Job> jobClass, JobKey jobKey,
            JobDataMap dataMap, QuartzComponent quartzComponent) {
        JobBuilder jobBuilder = JobBuilder.newJob(jobClass);
        jobBuilder.withIdentity(jobKey)
                // 设置任务的描述信息，方便在管理界面或日志中了解任务的用途。
                .withDescription(quartzComponent.getDescription())
                // 设置任务是否请求恢复。如果设置为 true，当 Quartz 节点在任务执行过程中发生故障并重启后，
                // 该任务会被重新执行（前提是任务实现了 StatefulJob 接口或有相应的恢复逻辑）。
                .requestRecovery(quartzComponent.isShouldRecover())
                // 设置任务是否持久化存储。如果设置为 true，即使没有 Trigger 关联该任务，任务也会保留在 Quartz 中，
                // 直到显式地删除它。这对于一些长期运行的任务或需要在特定条件下手动触发的任务很有用。
                .storeDurably(quartzComponent.isDurability());
        if (dataMap != null) {
            jobBuilder.usingJobData(dataMap);
        }
        return jobBuilder.build();
    }

    /**
     * 创建一个立即触发且仅触发一次的触发器。
     *
     * @param triggerKey 触发器的唯一标识，由触发器名称和所属组名组成，用于在Quartz中唯一标识该触发器。
     * @return 返回一个仅触发一次且立即启动的Trigger对象。
     */
    public static Trigger getTrigger(TriggerKey triggerKey) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startNow()
                .build();
    }

    /**
     * 获取一个按指定分钟间隔重复触发的触发器。
     *
     * @param triggerKey 触发器的唯一标识，由触发器名称和所属组名组成，用于在Quartz中唯一标识该触发器。
     * @param timeInterval 触发器触发的时间间隔，单位为分钟。
     * @return 返回一个按指定分钟间隔无限循环触发的Trigger对象。
     */
    public static Trigger getMinuteSimpleTrigger(TriggerKey triggerKey, int timeInterval) {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(timeInterval)
                // 配置触发器无限循环触发
                .repeatForever()
                // 配置错过触发的处理策略：保持剩余触发次数，跳过错过的触发器
                .withMisfireHandlingInstructionNextWithRemainingCount();
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startNow()
                .withSchedule(scheduleBuilder)
                .build();
    }

    public static Trigger getHoursSimpleTrigger(TriggerKey triggerKey, int timeInterval) {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInHours(timeInterval)
                // 配置触发器无限循环触发
                .repeatForever()
                // 配置错过触发的处理策略：保持剩余触发次数，跳过错过的触发器
                .withMisfireHandlingInstructionNextWithRemainingCount();
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startNow()
                .withSchedule(scheduleBuilder)
                .build();
    }

    public static Trigger getSecondsSimpleTrigger(TriggerKey triggerKey, int timeInterval) {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(timeInterval)
                // 配置触发器无限循环触发
                .repeatForever()
                // 配置错过触发的处理策略：保持剩余触发次数，跳过错过的触发器
                .withMisfireHandlingInstructionNextWithRemainingCount();
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startNow()
                .withSchedule(scheduleBuilder)
                .build();
    }

    /**
     * 获取触发器的TriggerKey，使用默认的组名和前缀加上传入的任务名称来构建TriggerKey。
     * *
     * @param taskName 任务名称，将与前缀拼接后作为触发器键的名称部分。
     * @return 返回一个TriggerKey对象，用于标识特定的触发器。
     */
    public static TriggerKey getTriggerKey(String taskName) {
        return TriggerKey.triggerKey(QuartzSign.TRIGGER_KEY_PREFIX + taskName, QuartzSign.GROUP);
    }

    /**
     * 根据传入的名称和组名获取触发器的TriggerKey。
     *
     * @param name 触发器的名称。
     * @param group 触发器所属的组名。
     * @return 返回一个TriggerKey对象，用于标识特定的触发器。
     */
    public static TriggerKey getTriggerKey(String name, String group) {
        return TriggerKey.triggerKey(name, group);
    }

    public static TriggerKey getDefaultTriggerKey() {
        return TriggerKey.triggerKey(QuartzSign.TRIGGER_KEY_PREFIX, QuartzSign.GROUP);
    }

    /**
     * 通过任务名称获取JobKey，使用默认的组名和前缀加上传入的任务名称来构建JobKey。
     *
     * @param taskName 任务名称，将与前缀拼接后作为任务键的名称部分。
     * @return 返回一个JobKey对象，用于标识特定的任务。
     */
    public static JobKey getJobKey(String taskName) {
        return JobKey.jobKey(QuartzSign.JOB_KEY_PREFIX + taskName, QuartzSign.GROUP);
    }

    /**
     * 根据传入的名称和组名获取JobKey。
     *
     * @param name 任务的名称。
     * @param group 任务所属的组名。
     * @return 返回一个JobKey对象，用于标识特定的任务。
     */
    public static JobKey getJobKey(String name, String group) {
        return JobKey.jobKey(name, group);
    }

    public static JobKey getDefaultJobKey() {
        return JobKey.jobKey(QuartzSign.JOB_KEY_PREFIX, QuartzSign.GROUP);
    }


}
