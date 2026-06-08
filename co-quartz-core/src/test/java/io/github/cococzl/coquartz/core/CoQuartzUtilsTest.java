package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CoQuartzUtilsTest {

    @Test
    void truncate_shortString_returnsSame() {
        assertThat(CoQuartzUtils.truncate("hello", 10)).isEqualTo("hello");
    }

    @Test
    void truncate_exactLength_returnsSame() {
        assertThat(CoQuartzUtils.truncate("hello", 5)).isEqualTo("hello");
    }

    @Test
    void truncate_longString_truncatesToMaxLength() {
        String result = CoQuartzUtils.truncate("hello world", 5);
        assertThat(result).isEqualTo("hello");
        assertThat(result).hasSize(5);
    }

    @Test
    void truncate_null_returnsNull() {
        assertThat(CoQuartzUtils.truncate(null, 10)).isNull();
    }

    @Test
    void truncate_emptyString_returnsEmpty() {
        assertThat(CoQuartzUtils.truncate("", 10)).isEmpty();
    }

    @Test
    void getStackTraceAsString_returnsStackTrace() {
        Exception e = new RuntimeException("test error");
        String result = CoQuartzUtils.getStackTraceAsString(e);

        assertThat(result).contains("RuntimeException");
        assertThat(result).contains("test error");
    }

    @Test
    void getStackTraceAsString_null_returnsNull() {
        assertThat(CoQuartzUtils.getStackTraceAsString(null)).isNull();
    }

    @Test
    void getStackTraceAsString_withCause_includesCause() {
        Exception cause = new IllegalStateException("root cause");
        Exception e = new RuntimeException("wrapper", cause);
        String result = CoQuartzUtils.getStackTraceAsString(e);

        assertThat(result).contains("RuntimeException");
        assertThat(result).contains("IllegalStateException");
        assertThat(result).contains("root cause");
    }
}