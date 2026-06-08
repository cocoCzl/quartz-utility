package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NonConcurrentJobWrapperTest {

    @Test
    void execute_delegatesToWrappedJob() throws Exception {
        Job delegate = mock(Job.class);
        JobExecutionContext context = mock(JobExecutionContext.class);
        NonConcurrentJobWrapper wrapper = new NonConcurrentJobWrapper(delegate);

        wrapper.execute(context);

        verify(delegate).execute(context);
    }

    @Test
    void getDelegate_returnsWrappedJob() {
        Job delegate = mock(Job.class);
        NonConcurrentJobWrapper wrapper = new NonConcurrentJobWrapper(delegate);

        assertThat(wrapper.getDelegate()).isSameAs(delegate);
    }

    @Test
    void execute_propagatesException() throws Exception {
        Job delegate = mock(Job.class);
        JobExecutionContext context = mock(JobExecutionContext.class);
        doThrow(new JobExecutionException("test")).when(delegate).execute(context);
        NonConcurrentJobWrapper wrapper = new NonConcurrentJobWrapper(delegate);

        assertThatThrownBy(() -> wrapper.execute(context))
                .isInstanceOf(JobExecutionException.class);
    }

    @Test
    void class_hasDisallowConcurrentExecutionAnnotation() {
        assertThat(NonConcurrentJobWrapper.class)
                .hasAnnotation(org.quartz.DisallowConcurrentExecution.class);
    }
}