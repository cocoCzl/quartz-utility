package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.quartz.*;
import java.util.*;

/** Explicit tombstones only. Never infer deletion from tasks missing on a node's classpath. */
public record DefinitionMigrationPlan(String applicationNamespace, Set<JobKey> retiredJobs) {
    public DefinitionMigrationPlan {
        if (applicationNamespace == null || applicationNamespace.isBlank()) throw new IllegalArgumentException("namespace required");
        retiredJobs = Set.copyOf(retiredJobs);
    }
    public List<JobKey> preview(Scheduler scheduler) throws SchedulerException {
        List<JobKey> found = new ArrayList<>();
        for (JobKey key : retiredJobs) {
            JobDetail job = scheduler.getJobDetail(key);
            if (job == null) continue;
            JobDataMap data = job.getJobDataMap();
            if (!applicationNamespace.equals(data.getString(CoQuartzConstants.APPLICATION_NAMESPACE))
                    || !CoQuartzConstants.OWNER_VALUE.equals(data.getString(CoQuartzConstants.OWNER))
                    || !Boolean.parseBoolean(String.valueOf(data.get(CoQuartzConstants.CODE_OWNED)))) {
                throw new CoQuartzConfigurationException("Refusing to retire task outside code ownership/namespace: " + key);
            }
            found.add(key);
        }
        return List.copyOf(found);
    }
    /** Requires all cluster nodes stopped/standby and a single migration owner, as documented. */
    public List<JobKey> apply(Scheduler scheduler) throws SchedulerException {
        if (!scheduler.isInStandbyMode()) throw new CoQuartzConfigurationException("Migration requires scheduler standby");
        List<JobKey> found = preview(scheduler);
        for (JobKey key : found) scheduler.deleteJob(key);
        return found;
    }
}
