package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;
import org.quartz.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class QuartzNativeCapabilitiesTest {
    @PersistJobDataAfterExecution
    public static class PersistentJob implements Job {
        static AtomicInteger observed = new AtomicInteger();
        public void execute(JobExecutionContext context) {
            var data = context.getJobDetail().getJobDataMap();
            int next = data.containsKey("count") ? data.getInt("count") + 1 : 1;
            data.put("count", next);
            observed.set(next);
        }
    }
    public static class InterruptJob implements InterruptableJob {
        static CountDownLatch started, signal, exit;
        public void execute(JobExecutionContext context) {
            started.countDown();
            try { exit.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        }
        public void interrupt() { signal.countDown(); exit.countDown(); }
    }
    @Test void persistentDelegateDataIsVisibleToNextExecution() throws Exception {
        var support = new BuilderScheduleRegressionTest();
        Scheduler scheduler = support.scheduler();
        try {
            scheduler.setJobFactory(new CoQuartzJobFactory());
            PersistentJob.observed.set(0);
            QuartzTaskBuilder.newBuilder().jobClass(PersistentJob.class).jobName("persistent")
                    .intervalInSeconds(3600).startAt(new java.util.Date(System.currentTimeMillis() + 3600000)).schedule(scheduler);
            assertThat(scheduler.getJobDetail(JobKey.jobKey("persistent")).isPersistJobDataAfterExecution()).isTrue();
            scheduler.start();
            for (int expected = 1; expected <= 2; expected++) {
                scheduler.triggerJob(JobKey.jobKey("persistent"));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while ((!scheduler.getJobDetail(JobKey.jobKey("persistent")).getJobDataMap().containsKey("count")
                        || scheduler.getJobDetail(JobKey.jobKey("persistent")).getJobDataMap().getInt("count") < expected)
                        && System.nanoTime() < deadline) Thread.sleep(10);
                assertThat(scheduler.getJobDetail(JobKey.jobKey("persistent")).getJobDataMap().getInt("count"))
                        .isEqualTo(expected);
            }
        } finally { scheduler.shutdown(true); }
    }
    @Test void schedulerInterruptReachesEnhancedBusinessJob() throws Exception {
        var support = new BuilderScheduleRegressionTest();
        Scheduler scheduler = support.scheduler();
        InterruptJob.started = new CountDownLatch(1);
        InterruptJob.signal = new CountDownLatch(1);
        InterruptJob.exit = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var factory = new CoQuartzJobFactory();
            factory.setTimeoutExecutor(executor);
            factory.setProperties(new io.github.cococzl.coquartz.config.CoQuartzProperties());
            scheduler.setJobFactory(factory);
            QuartzTaskBuilder.newBuilder().jobClass(InterruptJob.class).jobName("interrupt")
                    .intervalInSeconds(3600).timeout(5000)
                    .startAt(new java.util.Date(System.currentTimeMillis() + 3600000)).schedule(scheduler);
            scheduler.start();
            scheduler.triggerJob(JobKey.jobKey("interrupt"));
            assertThat(InterruptJob.started.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.interrupt(JobKey.jobKey("interrupt"))).isTrue();
            assertThat(InterruptJob.signal.await(1, TimeUnit.SECONDS)).isTrue();
        } finally { InterruptJob.exit.countDown(); scheduler.shutdown(true); executor.shutdownNow(); }
    }
}
