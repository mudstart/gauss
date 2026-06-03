package io.gauss.flume.scheduler;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Scheduled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ScheduledPipelineRegistry} and {@link CronExpression}.
 * Covers HU-013 acceptance criteria.
 */
class ScheduledPipelineRegistryTest {

    // -------------------------------------------------------------------------
    // Fixture pipeline classes
    // -------------------------------------------------------------------------

    @DataPipeline("daily-retrain")
    @Scheduled(cron = "0 2 * * *", description = "Daily retraining at 2 AM")
    static class DailyRetrainPipeline { }

    @DataPipeline("hourly-features")
    @Scheduled(cron = "0 * * * *", description = "Every hour")
    static class HourlyFeaturePipeline { }

    @DataPipeline("mixed-pipeline")
    static class MixedSchedulePipeline {
        @Scheduled(cron = "*/15 * * * *", description = "Every 15 minutes")
        public void refreshCache() { }

        @Scheduled(cron = "0 6 * * 1", description = "Monday mornings")
        public void weeklyReport() { }
    }

    @DataPipeline("no-schedule")
    static class NoSchedulePipeline { }

    static class NotAPipeline {
        @Scheduled(cron = "0 0 * * *")
        public void sometMethod() { }
    }

    // -------------------------------------------------------------------------
    // CronExpression — parsing
    // -------------------------------------------------------------------------

    @Test
    void cronParse_wildcard_valid() {
        assertThatNoException().isThrownBy(() -> CronExpression.parse("* * * * *"));
    }

    @Test
    void cronParse_typical_daily() {
        assertThatNoException().isThrownBy(() -> CronExpression.parse("0 2 * * *"));
    }

    @Test
    void cronParse_step_valid() {
        assertThatNoException().isThrownBy(() -> CronExpression.parse("*/15 * * * *"));
    }

    @Test
    void cronParse_range_valid() {
        assertThatNoException().isThrownBy(() -> CronExpression.parse("0 9-17 * * 1-5"));
    }

    @Test
    void cronParse_list_valid() {
        assertThatNoException().isThrownBy(() -> CronExpression.parse("0 8,12,18 * * *"));
    }

    @Test
    void cronParse_tooFewFields_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CronExpression.parse("0 2 * *"));
    }

    @Test
    void cronParse_tooManyFields_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CronExpression.parse("0 2 * * * *"));
    }

    @Test
    void cronParse_blank_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CronExpression.parse(""));
    }

    @Test
    void cronParse_invalidMinute_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CronExpression.parse("60 * * * *"));
    }

    @Test
    void cronParse_invalidHour_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CronExpression.parse("0 24 * * *"));
    }

    // -------------------------------------------------------------------------
    // CronExpression — matches
    // -------------------------------------------------------------------------

    @Test
    void cronMatches_dailyAt2am() {
        CronExpression expr = CronExpression.parse("0 2 * * *");
        assertThat(expr.matches(LocalDateTime.of(2026, 5, 1, 2, 0))).isTrue();
        assertThat(expr.matches(LocalDateTime.of(2026, 5, 1, 3, 0))).isFalse();
        assertThat(expr.matches(LocalDateTime.of(2026, 5, 1, 2, 1))).isFalse();
    }

    @Test
    void cronMatches_everyMinute() {
        CronExpression expr = CronExpression.parse("* * * * *");
        assertThat(expr.matches(LocalDateTime.of(2026, 1, 15, 10, 30))).isTrue();
        assertThat(expr.matches(LocalDateTime.of(2026, 6,  1,  0,  0))).isTrue();
    }

    @Test
    void cronMatches_everyFifteenMinutes() {
        CronExpression expr = CronExpression.parse("*/15 * * * *");
        assertThat(expr.matches(LocalDateTime.of(2026, 3, 1, 8,  0))).isTrue();
        assertThat(expr.matches(LocalDateTime.of(2026, 3, 1, 8, 15))).isTrue();
        assertThat(expr.matches(LocalDateTime.of(2026, 3, 1, 8, 30))).isTrue();
        assertThat(expr.matches(LocalDateTime.of(2026, 3, 1, 8,  7))).isFalse();
    }

    // -------------------------------------------------------------------------
    // CronExpression — nextFire
    // -------------------------------------------------------------------------

    @Test
    void nextFire_findsNextMinuteForEveryMinuteCron() {
        CronExpression expr = CronExpression.parse("* * * * *");
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertThat(expr.nextFire(from)).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 1));
    }

    @Test
    void nextFire_rollsOverHour() {
        CronExpression expr = CronExpression.parse("0 * * * *");
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 30);
        assertThat(expr.nextFire(from)).isEqualTo(LocalDateTime.of(2026, 1, 1, 11, 0));
    }

    @Test
    void nextFire_dailyAt2am_nextDay() {
        CronExpression expr = CronExpression.parse("0 2 * * *");
        LocalDateTime from = LocalDateTime.of(2026, 5, 1, 2, 0);  // exactly at 2:00
        assertThat(expr.nextFire(from)).isEqualTo(LocalDateTime.of(2026, 5, 2, 2, 0));
    }

    // -------------------------------------------------------------------------
    // ScheduledPipelineRegistry — scan
    // -------------------------------------------------------------------------

    @Test
    void scan_classLevelSchedule_returnsOneDescriptor() {
        List<ScheduledPipelineDescriptor> descriptors =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class);
        assertThat(descriptors).hasSize(1);
    }

    @Test
    void scan_classLevelSchedule_correctPipelineName() {
        ScheduledPipelineDescriptor d =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class).get(0);
        assertThat(d.pipelineName()).isEqualTo("daily-retrain");
    }

    @Test
    void scan_classLevelSchedule_correctCron() {
        ScheduledPipelineDescriptor d =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class).get(0);
        assertThat(d.cronExpression()).isEqualTo("0 2 * * *");
    }

    @Test
    void scan_classLevelSchedule_correctDescription() {
        ScheduledPipelineDescriptor d =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class).get(0);
        assertThat(d.description()).isEqualTo("Daily retraining at 2 AM");
    }

    @Test
    void scan_classLevelSchedule_correctClass() {
        ScheduledPipelineDescriptor d =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class).get(0);
        assertThat(d.pipelineClass()).isEqualTo(DailyRetrainPipeline.class);
    }

    @Test
    void scan_methodLevelSchedules_returnsOnePerAnnotatedMethod() {
        List<ScheduledPipelineDescriptor> descriptors =
                ScheduledPipelineRegistry.scan(MixedSchedulePipeline.class);
        assertThat(descriptors).hasSize(2);
    }

    @Test
    void scan_methodLevelSchedule_nameHasMethodPrefix() {
        List<ScheduledPipelineDescriptor> descriptors =
                ScheduledPipelineRegistry.scan(MixedSchedulePipeline.class);
        assertThat(descriptors)
                .anyMatch(d -> d.pipelineName().startsWith("method:mixed-pipeline."));
    }

    @Test
    void scan_noScheduleClass_returnsEmpty() {
        assertThat(ScheduledPipelineRegistry.scan(NoSchedulePipeline.class)).isEmpty();
    }

    @Test
    void scan_nonPipelineClass_returnsEmpty() {
        assertThat(ScheduledPipelineRegistry.scan(NotAPipeline.class)).isEmpty();
    }

    @Test
    void scan_multiplePipelineClasses_aggregatesResults() {
        List<ScheduledPipelineDescriptor> descriptors =
                ScheduledPipelineRegistry.scan(
                        DailyRetrainPipeline.class,
                        HourlyFeaturePipeline.class);
        assertThat(descriptors).hasSize(2);
    }

    @Test
    void scanClass_invalidCron_throwsIllegalArgument() {
        @DataPipeline("bad-cron")
        @Scheduled(cron = "99 99 * * *")
        class BadCronPipeline { }

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ScheduledPipelineRegistry.scanClass(BadCronPipeline.class));
    }

    // -------------------------------------------------------------------------
    // ScheduledPipelineDescriptor — cron() helper
    // -------------------------------------------------------------------------

    @Test
    void descriptor_cron_returnsParsedExpression() {
        ScheduledPipelineDescriptor d =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class).get(0);
        assertThat(d.cron().expression()).isEqualTo("0 2 * * *");
    }

    // -------------------------------------------------------------------------
    // findByName
    // -------------------------------------------------------------------------

    @Test
    void findByName_returnsMatchingDescriptor() {
        List<ScheduledPipelineDescriptor> all =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class,
                                                HourlyFeaturePipeline.class);
        assertThat(ScheduledPipelineRegistry.findByName(all, "daily-retrain")).isPresent();
        assertThat(ScheduledPipelineRegistry.findByName(all, "hourly-features")).isPresent();
    }

    @Test
    void findByName_returnsEmpty_whenNotFound() {
        List<ScheduledPipelineDescriptor> all =
                ScheduledPipelineRegistry.scan(DailyRetrainPipeline.class);
        assertThat(ScheduledPipelineRegistry.findByName(all, "nonexistent")).isEmpty();
    }
}
