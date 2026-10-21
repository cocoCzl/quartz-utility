package io.github.cococzl.coquartz.core;

import org.quartz.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one Spring-created execution instance, including its proxy and destruction callback. */
final class ManagedJob implements InterruptableJob, AutoCloseable {
    private final Job delegate;
    private final Object target;
    private final Runnable destroy;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Thread worker;

    ManagedJob(Job delegate, Object target, Runnable destroy) {
        this.delegate = delegate; this.target = target; this.destroy = destroy;
    }
    public void execute(JobExecutionContext context) throws JobExecutionException {
        worker = Thread.currentThread();
        boolean finalInvocation = true;
        try { delegate.execute(context); }
        catch (JobExecutionException error) {
            finalInvocation = !error.refireImmediately();
            throw error;
        }
        finally { worker = null; if (finalInvocation) close(); }
    }
    public void interrupt() throws UnableToInterruptJobException {
        try { if (target instanceof InterruptableJob job) job.interrupt(); }
        finally { Thread thread = worker; if (thread != null) thread.interrupt(); }
    }
    public void close() { if (closed.compareAndSet(false, true)) destroy.run(); }
}
