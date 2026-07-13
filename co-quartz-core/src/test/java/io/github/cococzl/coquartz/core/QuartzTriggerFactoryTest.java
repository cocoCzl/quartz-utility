package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuartzTriggerFactoryTest {

    @Test
    @ResourceLock("default-time-zone")
    void explicitTimeZoneMakesCronIndependentFromJvmDefault() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            CronTrigger first = cron("UTC", MisfirePolicy.SMART_POLICY);
            Date firstFire = first.getFireTimeAfter(Date.from(Instant.parse("2030-01-01T00:00:00Z")));

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            CronTrigger second = cron("UTC", MisfirePolicy.SMART_POLICY);
            Date secondFire = second.getFireTimeAfter(Date.from(Instant.parse("2030-01-01T00:00:00Z")));

            assertThat(first.getTimeZone().getID()).isEqualTo("UTC");
            assertThat(second.getTimeZone().getID()).isEqualTo("UTC");
            assertThat(secondFire).isEqualTo(firstFire);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void cronTimeZoneHonorsDaylightSavingTransitions() {
        CronTrigger trigger = (CronTrigger) QuartzTriggerFactory.build(
                "trigger", "DEFAULT", "0 0 9 * * ?", 0,
                "America/New_York", MisfirePolicy.SMART_POLICY, null, null);

        Date winter = trigger.getFireTimeAfter(Date.from(Instant.parse("2030-01-15T00:00:00Z")));
        Date summer = trigger.getFireTimeAfter(Date.from(Instant.parse("2030-07-15T00:00:00Z")));

        assertThat(winter.toInstant()).isEqualTo(Instant.parse("2030-01-15T14:00:00Z"));
        assertThat(summer.toInstant()).isEqualTo(Instant.parse("2030-07-15T13:00:00Z"));
    }

    @Test
    void rejectsInvalidTimeZone() {
        assertThatThrownBy(() -> cron("Mars/Olympus", MisfirePolicy.SMART_POLICY))
                .isInstanceOf(CoQuartzConfigurationException.class)
                .hasMessageContaining("Mars/Olympus");
    }

    @Test
    void rejectsInvalidCron() {
        assertThatThrownBy(() -> QuartzTriggerFactory.build(
                "trigger", "DEFAULT", "not-a-cron", 0,
                "UTC", MisfirePolicy.SMART_POLICY, null, null))
                .isInstanceOf(CoQuartzConfigurationException.class)
                .hasMessageContaining("not-a-cron");
    }

    @Test
    void rejectsCronAndIntervalTogether() {
        assertThatThrownBy(() -> QuartzTriggerFactory.build(
                "trigger", "DEFAULT", "0 0 * * * ?", 60,
                "UTC", MisfirePolicy.SMART_POLICY, null, null))
                .isInstanceOf(CoQuartzConfigurationException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void rejectsMissingCronAndInterval() {
        assertThatThrownBy(() -> QuartzTriggerFactory.build(
                "trigger", "DEFAULT", "", 0,
                "UTC", MisfirePolicy.SMART_POLICY, null, null))
                .isInstanceOf(CoQuartzConfigurationException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void cronMisfirePoliciesMapToQuartzInstructions() {
        assertThat(cron("UTC", MisfirePolicy.SMART_POLICY).getMisfireInstruction())
                .isEqualTo(Trigger.MISFIRE_INSTRUCTION_SMART_POLICY);
        assertThat(cron("UTC", MisfirePolicy.FIRE_NOW).getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(cron("UTC", MisfirePolicy.IGNORE_MISFIRES).getMisfireInstruction())
                .isEqualTo(Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
    }

    @Test
    void intervalMisfirePoliciesMapToQuartzInstructions() {
        assertThat(interval(MisfirePolicy.SMART_POLICY).getMisfireInstruction())
                .isEqualTo(Trigger.MISFIRE_INSTRUCTION_SMART_POLICY);
        assertThat(interval(MisfirePolicy.FIRE_NOW).getMisfireInstruction())
                .isEqualTo(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        assertThat(interval(MisfirePolicy.IGNORE_MISFIRES).getMisfireInstruction())
                .isEqualTo(Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
    }

    private CronTrigger cron(String timeZone, MisfirePolicy policy) {
        return (CronTrigger) QuartzTriggerFactory.build(
                "trigger", "DEFAULT", "0 0 9 * * ?", 0,
                timeZone, policy, null, null);
    }

    private SimpleTrigger interval(MisfirePolicy policy) {
        return (SimpleTrigger) QuartzTriggerFactory.build(
                "trigger", "DEFAULT", "", 60,
                "UTC", policy, null, null);
    }
}
