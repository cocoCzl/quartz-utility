package io.github.cococzl.coquartz.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LogTaskExecStateEnumTest {

    @Test
    void successCode() {
        assertThat(LogTaskExecStateEnum.SUCCESS.getCode()).isEqualTo(1);
    }

    @Test
    void failCode() {
        assertThat(LogTaskExecStateEnum.FAIL.getCode()).isEqualTo(0);
    }

    @Test
    void unknownCode() {
        assertThat(LogTaskExecStateEnum.UNKNOWN.getCode()).isEqualTo(-99);
    }

    @Test
    void parseSuccessCode() {
        assertThat(LogTaskExecStateEnum.parse(1)).isEqualTo(LogTaskExecStateEnum.SUCCESS);
    }

    @Test
    void parseFailCode() {
        assertThat(LogTaskExecStateEnum.parse(0)).isEqualTo(LogTaskExecStateEnum.FAIL);
    }

    @Test
    void parseUnknownCode() {
        assertThat(LogTaskExecStateEnum.parse(-99)).isEqualTo(LogTaskExecStateEnum.UNKNOWN);
    }

    @Test
    void parseInvalidCode_returnsUnknown() {
        assertThat(LogTaskExecStateEnum.parse(999)).isEqualTo(LogTaskExecStateEnum.UNKNOWN);
        assertThat(LogTaskExecStateEnum.parse(-1)).isEqualTo(LogTaskExecStateEnum.UNKNOWN);
    }

    @Test
    void enumValues() {
        assertThat(LogTaskExecStateEnum.values()).containsExactlyInAnyOrder(
                LogTaskExecStateEnum.SUCCESS,
                LogTaskExecStateEnum.FAIL,
                LogTaskExecStateEnum.UNKNOWN
        );
    }
}