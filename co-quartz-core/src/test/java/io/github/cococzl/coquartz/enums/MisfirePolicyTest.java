package io.github.cococzl.coquartz.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MisfirePolicyTest {

    @Test
    void enumValues() {
        assertThat(MisfirePolicy.values()).containsExactly(
                MisfirePolicy.SMART_POLICY,
                MisfirePolicy.FIRE_NOW,
                MisfirePolicy.IGNORE_MISFIRES
        );
    }

    @Test
    void valueOf() {
        assertThat(MisfirePolicy.valueOf("SMART_POLICY")).isEqualTo(MisfirePolicy.SMART_POLICY);
        assertThat(MisfirePolicy.valueOf("FIRE_NOW")).isEqualTo(MisfirePolicy.FIRE_NOW);
        assertThat(MisfirePolicy.valueOf("IGNORE_MISFIRES")).isEqualTo(MisfirePolicy.IGNORE_MISFIRES);
    }
}