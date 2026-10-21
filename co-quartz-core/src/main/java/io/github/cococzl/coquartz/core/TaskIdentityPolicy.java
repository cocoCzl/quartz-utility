package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.exception.CodeOwnedTaskModificationException;
import io.github.cococzl.coquartz.exception.CoQuartzSchedulingException;
import org.quartz.*;

/** Shared identity checks for public mutation APIs. Raw Scheduler access retains Quartz semantics. */
public final class TaskIdentityPolicy {
    private TaskIdentityPolicy() { }

    public static void assertMutable(JobDetail job) {
        if (job != null && Boolean.parseBoolean(String.valueOf(job.getJobDataMap().get(CoQuartzConstants.CODE_OWNED)))) {
            throw new CodeOwnedTaskModificationException("Task " + job.getKey()
                    + " is owned by application code; modify its code definition instead of using the management API");
        }
    }

    public static void assertDynamic(JobDetail job) {
        assertMutable(job);
        if (job != null && (!CoQuartzConstants.OWNER_VALUE.equals(job.getJobDataMap().get(CoQuartzConstants.OWNER))
                || !CoQuartzConstants.SOURCE_DYNAMIC.equals(job.getJobDataMap().get(CoQuartzConstants.TASK_SOURCE)))) {
            throw new CoQuartzSchedulingException("Task " + job.getKey() + " is not owned by the dynamic Builder");
        }
    }

    public static void assertPublicGroup(String group) {
        if (group != null && (group.startsWith("CO_QUARTZ") || group.startsWith("RECOVERING_JOBS")
                || group.equals(Scheduler.DEFAULT_FAIL_OVER_GROUP))) {
            throw new CoQuartzSchedulingException("Reserved scheduler namespace: " + group);
        }
    }

    public static void assertTriggerTarget(Scheduler scheduler, TriggerKey key, JobKey job) throws SchedulerException {
        assertPublicGroup(key.getGroup());
        Trigger existing = scheduler.getTrigger(key);
        if (existing != null && !job.equals(existing.getJobKey())) {
            throw new CoQuartzSchedulingException("Trigger " + key + " belongs to another task: " + existing.getJobKey());
        }
    }
}
