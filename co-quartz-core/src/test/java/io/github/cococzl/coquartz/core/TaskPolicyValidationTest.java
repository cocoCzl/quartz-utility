package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaskPolicyValidationTest {
    @Test void definitionsAndDynamicBuilderRejectInvalidExecutionPolicies() throws Exception {
        var support = new BuilderScheduleRegressionTest();
        var scheduler = support.scheduler();
        try {
            assertThatThrownBy(() -> support.builder("negative").retryTimes(-1).schedule(scheduler))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("retryTimes");
            assertThatThrownBy(() -> support.builder("nan").backoffMultiplier(Double.NaN).schedule(scheduler))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("backoffMultiplier");
            assertThatThrownBy(() -> new QuartzJobDefinition.Builder().retryTimes(-1).build())
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("retryTimes");
            assertThatThrownBy(() -> new RetryContext(1, Long.MAX_VALUE, true, Double.POSITIVE_INFINITY))
                    .isInstanceOf(RuntimeException.class);
        } finally { scheduler.shutdown(true); }
    }
}
