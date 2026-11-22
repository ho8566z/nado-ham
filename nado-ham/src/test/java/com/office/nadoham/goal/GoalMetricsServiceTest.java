package com.office.nadoham.goal;

import com.office.nadoham.config.TimeConfig;
import com.office.nadoham.goal.mapper.GoalMetricsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

// 지표 계산: 핵심 지표(놓친 뒤 7일 안 재개)·보조 지표의 성공/실패/관찰 중 구분과 비율
class GoalMetricsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static MetricRows.MissedUser missed(String owner, LocalDate firstMiss, boolean recovered) {
        return new MetricRows.MissedUser(owner, firstMiss, recovered);
    }

    @Test
    @DisplayName("핵심 지표: 회복했으면 성공, 기간(첫 미실행+7일)이 남았으면 관찰 중, 끝났는데 못 했으면 실패이고 관찰 중은 비율에서 뺀다")
    void recovery() {
        GoalMetrics.Recovery r = GoalMetricsService.recovery(List.of(
                missed("a", TODAY.minusDays(10), true),      // 성공
                missed("b", TODAY.minusDays(10), false),     // 기간 끝남 → 실패
                missed("c", TODAY.minusDays(3), false),      // 기간 남음 → 관찰 중
                missed("d", TODAY.minusDays(2), true),       // 기간이 남았어도 이미 회복 → 성공
                missed("e", TODAY.minusDays(7), false)),     // 마지막 날(오늘이 첫 미실행+7일) → 아직 관찰 중
                TODAY);

        assertThat(r.missedUsers()).isEqualTo(5);
        assertThat(r.recovered()).isEqualTo(2);
        assertThat(r.notRecovered()).isEqualTo(1);
        assertThat(r.pending()).isEqualTo(2);
        assertThat(r.rate()).isCloseTo(2.0 / 3, within(1e-9));
    }

    @Test
    @DisplayName("핵심 지표: 기간이 끝난 사용자가 아직 없으면 비율은 null, 놓친 사용자가 없으면 모두 0")
    void recoveryNoDenominator() {
        assertThat(GoalMetricsService.recovery(List.of(missed("c", TODAY.minusDays(1), false)), TODAY).rate()).isNull();
        GoalMetrics.Recovery empty = GoalMetricsService.recovery(List.of(), TODAY);
        assertThat(empty.missedUsers()).isZero();
        assertThat(empty.rate()).isNull();
    }

    @Test
    @DisplayName("첫 활동 완료 비율: 완료(짧게 완료 포함)는 성공, 오늘 이후 예정은 관찰 중, 놓침·건너뜀은 미완료")
    void firstActivity() {
        GoalMetrics.FirstActivity f = GoalMetricsService.firstActivity(List.of(
                new MetricRows.FirstActivityRow("a", "DONE", TODAY.minusDays(5)),
                new MetricRows.FirstActivityRow("b", "SHORT_DONE", TODAY.minusDays(5)),
                new MetricRows.FirstActivityRow("c", "PLANNED", TODAY.minusDays(1)),     // 놓침 → 미완료
                new MetricRows.FirstActivityRow("d", "SKIPPED", TODAY.minusDays(2)),
                new MetricRows.FirstActivityRow("e", "PLANNED", TODAY),                  // 오늘 예정 → 관찰 중
                new MetricRows.FirstActivityRow("f", "PLANNED", TODAY.plusDays(2))), TODAY);

        assertThat(f.users()).isEqualTo(6);
        assertThat(f.completed()).isEqualTo(2);
        assertThat(f.notCompleted()).isEqualTo(2);
        assertThat(f.pending()).isEqualTo(2);
        assertThat(f.rate()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("과정 마무리 비율: 기간이 끝났거나 마무리한 사용자만 분모, 마무리한 사용자가 분자")
    void completion() {
        GoalMetrics.Completion c = GoalMetricsService.completion(List.of(
                new MetricRows.CourseUser("a", true, true),
                new MetricRows.CourseUser("b", false, true),
                new MetricRows.CourseUser("c", false, false)));      // 아직 진행 중 → 대상 아님

        assertThat(c.endedUsers()).isEqualTo(2);
        assertThat(c.completedUsers()).isEqualTo(1);
        assertThat(c.rate()).isCloseTo(0.5, within(1e-9));
        assertThat(GoalMetricsService.completion(List.of()).rate()).isNull();
    }

    @Test
    @DisplayName("활동 상태 수와 5분만 하고 완료한 비중(완료 중)")
    void activities() {
        GoalMetrics.Activities a = GoalMetricsService.activities(List.of(
                new MetricRows.StatusCount("PLANNED", 5), new MetricRows.StatusCount("DONE", 6),
                new MetricRows.StatusCount("SHORT_DONE", 2), new MetricRows.StatusCount("SKIPPED", 1)));

        assertThat(a).extracting(GoalMetrics.Activities::planned, GoalMetrics.Activities::done,
                GoalMetrics.Activities::shortDone, GoalMetrics.Activities::skipped).containsExactly(5L, 6L, 2L, 1L);
        assertThat(a.shortShare()).isCloseTo(0.25, within(1e-9));
        assertThat(GoalMetricsService.activities(List.of()).shortShare()).isNull();
    }

    @Test
    @DisplayName("compute: 오늘 날짜를 기준으로 네 쿼리를 모아 지표를 만든다")
    void compute() {
        GoalMetricsMapper mapper = Mockito.mock(GoalMetricsMapper.class);
        when(mapper.selectMissedUsers(TODAY)).thenReturn(List.of(missed("a", TODAY.minusDays(9), true)));
        when(mapper.selectFirstActivities()).thenReturn(List.of(new MetricRows.FirstActivityRow("a", "DONE", TODAY.minusDays(9))));
        when(mapper.selectCourseUsers(TODAY)).thenReturn(List.of(new MetricRows.CourseUser("a", false, false)));
        when(mapper.selectActivityStatusCounts()).thenReturn(List.of(new MetricRows.StatusCount("DONE", 1)));
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

        GoalMetrics metrics = new GoalMetricsService(mapper, clock).compute();

        assertThat(metrics.asOf()).isEqualTo("2026-11-22");
        assertThat(metrics.recovery().rate()).isEqualTo(1.0);
        assertThat(metrics.firstActivity().rate()).isEqualTo(1.0);
        assertThat(metrics.completion().rate()).isNull();
        assertThat(metrics.activities().done()).isEqualTo(1L);
    }

}
