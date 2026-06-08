package io.github.cococzl.coquartz.core;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
public class NonConcurrentJobWrapper implements Job {

    private final Job delegate;

    public NonConcurrentJobWrapper(Job delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        delegate.execute(context);
    }

    public Job getDelegate() {
        return delegate;
    }
}