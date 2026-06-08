package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RetryContextTest {

    @Test
    void noRetry_canRetryReturnsFalse() {
        RetryContext ctx = new RetryContext(0, 1000, false, 1.5);
        ctx.recordAttempt();
        assertThat(ctx.canRetry()).isFalse();
    }

    @Test
    void withRetry_canRetryReturnsTrue() {
        RetryContext ctx = new RetryContext(2, 1000, false, 1.5);
        ctx.recordAttempt();
        assertThat(ctx.canRetry()).isTrue();
    }

    @Test
    void retryExhausted_canRetryReturnsFalse() {
        RetryContext ctx = new RetryContext(2, 1000, false, 1.5);
        ctx.recordAttempt();
        ctx.recordAttempt();
        ctx.recordAttempt();
        assertThat(ctx.canRetry()).isFalse();
    }

    @Test
    void getCurrentAttempt() {
        RetryContext ctx = new RetryContext(3, 1000, false, 1.5);
        assertThat(ctx.getCurrentAttempt()).isEqualTo(0);
        ctx.recordAttempt();
        assertThat(ctx.getCurrentAttempt()).isEqualTo(1);
        ctx.recordAttempt();
        assertThat(ctx.getCurrentAttempt()).isEqualTo(2);
    }

    @Test
    void getMaxRetryTimes() {
        RetryContext ctx = new RetryContext(3, 1000, false, 1.5);
        assertThat(ctx.getMaxRetryTimes()).isEqualTo(3);
    }

    @Test
    void getNextRetryDelay_fixedInterval() {
        RetryContext ctx = new RetryContext(3, 1000, false, 1.5);
        assertThat(ctx.getNextRetryDelay()).isEqualTo(1000);
        ctx.recordAttempt();
        assertThat(ctx.getNextRetryDelay()).isEqualTo(1000);
    }

    @Test
    void getNextRetryDelay_exponentialBackoff() {
        RetryContext ctx = new RetryContext(3, 100, true, 2.0);
        assertThat(ctx.getNextRetryDelay()).isEqualTo(100);
        ctx.recordAttempt();
        assertThat(ctx.getNextRetryDelay()).isEqualTo(200);
    }

    @Test
    void getNextRetryDelay_exponentialBackoff_cappedAt60s() {
        RetryContext ctx = new RetryContext(10, 30000, true, 2.0);
        assertThat(ctx.getNextRetryDelay()).isEqualTo(30000);
        ctx.recordAttempt();
        assertThat(ctx.getNextRetryDelay()).isEqualTo(60000);
    }

    @Test
    void reset_restoresAttemptCountAndDelay() {
        RetryContext ctx = new RetryContext(2, 1000, true, 2.0);
        ctx.recordAttempt();
        ctx.getNextRetryDelay();
        assertThat(ctx.getCurrentAttempt()).isEqualTo(1);

        ctx.reset();

        assertThat(ctx.getCurrentAttempt()).isEqualTo(0);
        assertThat(ctx.getNextRetryDelay()).isEqualTo(1000);
    }
}