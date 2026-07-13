package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzConcurrencyTest {

    private Scheduler scheduler;

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    void nonConcurrentDynamicTaskExecutionIntervalsDoNotOverlap() throws Exception {
        scheduler = scheduler();
        BlockingJob.reset();
        schedule("serial", false);

        scheduler.triggerJob(JobKey.jobKey("serial", "DEFAULT"));
        assertThat(BlockingJob.firstStarted.await(3, TimeUnit.SECONDS)).isTrue();
        scheduler.triggerJob(JobKey.jobKey("serial", "DEFAULT"));

        Thread.sleep(200);
        assertThat(BlockingJob.starts.get()).isEqualTo(1);
        assertThat(BlockingJob.maxActive.get()).isEqualTo(1);

        BlockingJob.release.countDown();
        assertThat(BlockingJob.secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(BlockingJob.maxActive.get()).isEqualTo(1);
    }

    @Test
    void concurrentDynamicTaskExecutionIntervalsCanOverlap() throws Exception {
        scheduler = scheduler();
        BlockingJob.reset();
        schedule("parallel", true);

        scheduler.triggerJob(JobKey.jobKey("parallel", "DEFAULT"));
        scheduler.triggerJob(JobKey.jobKey("parallel", "DEFAULT"));

        assertThat(BlockingJob.secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(BlockingJob.maxActive.get()).isGreaterThanOrEqualTo(2);
        BlockingJob.release.countDown();
    }

    private Scheduler scheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "concurrency-" + System.nanoTime());
        properties.setProperty("org.quartz.threadPool.threadCount", "2");
        Scheduler result = new StdSchedulerFactory(properties).getScheduler();
        result.setJobFactory(new CoQuartzJobFactory());
        result.start();
        return result;
    }

    private void schedule(String name, boolean concurrent) {
        QuartzTaskBuilder.newBuilder()
                .jobClass(BlockingJob.class)
                .jobName(name)
                .intervalInSeconds(3600)
                .startAt(new java.util.Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .concurrent(concurrent)
                .schedule(scheduler);
    }

    public static class BlockingJob implements Job {
        static final AtomicInteger starts = new AtomicInteger();
        static final AtomicInteger active = new AtomicInteger();
        static final AtomicInteger maxActive = new AtomicInteger();
        static CountDownLatch firstStarted;
        static CountDownLatch secondStarted;
        static CountDownLatch release;

        static void reset() {
            starts.set(0);
            active.set(0);
            maxActive.set(0);
            firstStarted = new CountDownLatch(1);
            secondStarted = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        @Override
        public void execute(JobExecutionContext context) {
            int nowActive = active.incrementAndGet();
            maxActive.accumulateAndGet(nowActive, Math::max);
            int count = starts.incrementAndGet();
            if (count == 1) {
                firstStarted.countDown();
            }
            if (count == 2) {
                secondStarted.countDown();
            }
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        }
    }
}
