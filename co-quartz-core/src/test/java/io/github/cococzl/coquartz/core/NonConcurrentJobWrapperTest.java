package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NonConcurrentJobWrapperTest {

    @Test
    void execute_requiresTheCoQuartzJobFactory() {
        JobExecutionContext context = mock(JobExecutionContext.class);

        assertThatThrownBy(() -> new NonConcurrentJobWrapper().execute(context))
                .hasMessageContaining("CoQuartzJobFactory");
    }

    @Test
    void class_hasDisallowConcurrentExecutionAnnotation() {
        assertThat(NonConcurrentJobWrapper.class)
                .hasAnnotation(org.quartz.DisallowConcurrentExecution.class);
    }
}
