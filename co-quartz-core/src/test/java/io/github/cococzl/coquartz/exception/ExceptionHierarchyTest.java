package io.github.cococzl.coquartz.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    void coQuartzException_isRuntimeException() {
        CoQuartzException ex = new CoQuartzException("test");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void coQuartzException_messageOnly() {
        CoQuartzException ex = new CoQuartzException("test message");
        assertThat(ex.getMessage()).isEqualTo("test message");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void coQuartzException_messageAndCause() {
        RuntimeException cause = new RuntimeException("cause");
        CoQuartzException ex = new CoQuartzException("test message", cause);
        assertThat(ex.getMessage()).isEqualTo("test message");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void configurationException_extendsCoQuartzException() {
        CoQuartzConfigurationException ex = new CoQuartzConfigurationException("config error");
        assertThat(ex).isInstanceOf(CoQuartzException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("config error");
    }

    @Test
    void configurationException_withCause() {
        RuntimeException cause = new RuntimeException("root");
        CoQuartzConfigurationException ex = new CoQuartzConfigurationException("config error", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void schedulingException_extendsCoQuartzException() {
        CoQuartzSchedulingException ex = new CoQuartzSchedulingException("sched error");
        assertThat(ex).isInstanceOf(CoQuartzException.class);
        assertThat(ex.getMessage()).isEqualTo("sched error");
    }

    @Test
    void schedulingException_withCause() {
        RuntimeException cause = new RuntimeException("root");
        CoQuartzSchedulingException ex = new CoQuartzSchedulingException("sched error", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void executionException_extendsCoQuartzException() {
        CoQuartzExecutionException ex = new CoQuartzExecutionException("exec error");
        assertThat(ex).isInstanceOf(CoQuartzException.class);
        assertThat(ex.getMessage()).isEqualTo("exec error");
    }

    @Test
    void executionException_withCause() {
        RuntimeException cause = new RuntimeException("root");
        CoQuartzExecutionException ex = new CoQuartzExecutionException("exec error", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}