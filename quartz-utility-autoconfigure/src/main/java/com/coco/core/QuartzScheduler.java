package com.coco.core;

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

public class QuartzScheduler {

    private final Scheduler scheduler;

    public QuartzScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 安排一个简单间隔的任务，使用默认的 JobKey 和 TriggerKey，并携带任务数据。
     * 此方法会调用另一个重载的 scheduleSimpleIntervalJob 方法，将任务类、默认的 JobKey、TriggerKey、任务数据和 Quartz 组件信息传递过去。
     *
     * @param jobClass       实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param jobDataMap     任务数据映射，包含任务执行时需要使用的参数。可以为 null。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位等。
     * @throws SchedulerException 如果在调度任务过程中出现异常，如调度器未启动、任务或触发器注册失败等。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass, JobDataMap jobDataMap,
            QuartzComponent quartzComponent) throws SchedulerException {
        JobKey jobKey = SchedulerCore.getDefaultJobKey();
        TriggerKey triggerKey = SchedulerCore.getDefaultTriggerKey();
        scheduleSimpleIntervalJob(jobClass, jobKey, triggerKey, jobDataMap, null, quartzComponent);
    }

    /**
     * 安排一个简单间隔的任务，使用默认的 JobKey 和 TriggerKey，不携带任务数据。
     * 此方法会调用另一个重载的 scheduleSimpleIntervalJob 方法，将任务类、默认的 JobKey、TriggerKey 和 Quartz 组件信息传递过去。
     *
     * @param jobClass       实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位等。
     * @throws SchedulerException 如果在调度任务过程中出现异常，如调度器未启动、任务或触发器注册失败等。
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
     * 若任务不存在，则根据 Quartz 组件中的时间枚举创建相应的触发器，并将任务和触发器注册到调度器中。
     *
     * @param jobClass       实现了 Job 接口的任务类，指定了具体的任务逻辑。
     * @param jobKey         任务的唯一标识，用于在调度器中区分不同的任务。
     * @param triggerKey     触发器的唯一标识，用于在调度器中区分不同的触发器。
     * @param jobDataMap     任务数据映射，包含任务执行时需要使用的参数。可以为 null。
     * @param jobListener    任务监听器，用于监听任务的执行状态，如任务开始、结束等。可以为 null。
     * @param quartzComponent 包含任务调度相关配置信息的组件，如时间间隔、时间单位等。
     * @throws SchedulerException 如果在调度任务过程中出现异常，如调度器未启动、任务或触发器注册失败等。
     */
    public void scheduleSimpleIntervalJob(Class<? extends Job> jobClass, JobKey jobKey,
            TriggerKey triggerKey, JobDataMap jobDataMap, JobListener jobListener,
            QuartzComponent quartzComponent) throws SchedulerException {

        JobDetail jobDetail = SchedulerCore.getJobDetail(jobClass, jobKey, jobDataMap,
                quartzComponent);
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
                        quartzComponent.getTimeInterval());
                case MINUTES -> trigger = SchedulerCore.getMinuteSimpleTrigger(triggerKey,
                        quartzComponent.getTimeInterval());
                case SECONDS -> trigger = SchedulerCore.getSecondsSimpleTrigger(triggerKey,
                        quartzComponent.getTimeInterval());
                default -> throw new QuartzUtilityException("The interval type is abnormal",
                        QuartzUtilityException.PARAMETER_ABNORMAL);
            }
            if (jobListener != null) {
                scheduler.getListenerManager().addJobListener(jobListener);
            }
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

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
}
