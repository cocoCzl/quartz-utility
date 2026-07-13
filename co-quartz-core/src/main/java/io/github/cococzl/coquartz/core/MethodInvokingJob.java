package io.github.cococzl.coquartz.core;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz adapter that invokes a method on a Spring-managed bean.
 */
public class MethodInvokingJob implements Job {

    private MethodTaskRegistry methodTaskRegistry;

    @Autowired
    public void setMethodTaskRegistry(MethodTaskRegistry methodTaskRegistry) {
        this.methodTaskRegistry = methodTaskRegistry;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (methodTaskRegistry == null) {
            throw new JobExecutionException("Method task registry is not available");
        }
        methodTaskRegistry.invoke(context.getJobDetail().getKey());
    }
}
