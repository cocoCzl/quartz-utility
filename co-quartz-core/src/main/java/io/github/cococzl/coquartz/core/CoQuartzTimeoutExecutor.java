package io.github.cococzl.coquartz.core;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded executor for cooperative timeout work; saturation rejects immediately. */
public class CoQuartzTimeoutExecutor extends ThreadPoolExecutor implements AutoCloseable {

    private final long shutdownAwaitMs;

    public CoQuartzTimeoutExecutor(int coreSize, int maxSize, long shutdownAwaitMs, ThreadFactory threadFactory) {
        super(coreSize, maxSize, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory,
                new AbortPolicy());
        this.shutdownAwaitMs = shutdownAwaitMs;
        allowCoreThreadTimeOut(true);
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(shutdownAwaitMs, TimeUnit.MILLISECONDS)) {
                shutdownNow();
            }
        } catch (InterruptedException e) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
