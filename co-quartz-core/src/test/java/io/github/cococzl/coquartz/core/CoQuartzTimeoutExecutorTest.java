package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoQuartzTimeoutExecutorTest {

    @Test
    void saturationRejectsImmediatelyInsteadOfQueuing() throws Exception {
        CoQuartzTimeoutExecutor executor = new CoQuartzTimeoutExecutor(1, 1, 1000, runnable -> new Thread(runnable));
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> { running.countDown(); release.await(); return null; });
        assertThat(running.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> executor.submit(() -> null))
                .isInstanceOf(RejectedExecutionException.class);

        release.countDown();
        executor.close();
    }
}
