package io.github.cococzl.coquartz.core;

import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.SimpleTrigger;

/** Validates persisted framework trigger metadata; names alone confer no ownership. */
public final class InternalTriggers {
    private InternalTriggers() { }

    public static boolean isRetry(Trigger trigger, JobKey jobKey) {
        var data = trigger.getJobDataMap();
        Object attempt = data.get(CoQuartzConstants.RETRY_ATTEMPT);
        int number;
        try { number = Integer.parseInt(String.valueOf(attempt)); }
        catch (NumberFormatException invalid) { return false; }
        return CoQuartzConstants.RETRY_TRIGGER_GROUP.equals(trigger.getKey().getGroup())
                && jobKey.equals(trigger.getJobKey())
                && trigger instanceof SimpleTrigger simple && simple.getRepeatCount() == 0
                && CoQuartzConstants.OWNER_VALUE.equals(data.get(CoQuartzConstants.OWNER))
                && CoQuartzConstants.METADATA_VERSION_VALUE.equals(data.get(CoQuartzConstants.METADATA_VERSION))
                && jobKey.toString().equals(data.get(CoQuartzConstants.RETRY_JOB_KEY))
                && data.get(CoQuartzConstants.RETRY_EXECUTION_ID) instanceof String id && !id.isBlank()
                && number >= 2;
    }
}
