package io.github.cococzl.coquartz.core;

import org.quartz.Job;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Stable JobDetail class used for tasks that prohibit concurrent execution.
 * The job factory replaces this marker with the configured delegate before execution.
 */
@DisallowConcurrentExecution
public class NonConcurrentJobWrapper implements Job {

    public NonConcurrentJobWrapper() {
    }

    /**
     * @deprecated Quartz must see this class in the JobDetail, not a runtime wrapper instance.
     */
    @Deprecated
    public NonConcurrentJobWrapper(Job delegate) {
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        throw new JobExecutionException("NonConcurrentJobWrapper must be instantiated by CoQuartzJobFactory");
    }
}
