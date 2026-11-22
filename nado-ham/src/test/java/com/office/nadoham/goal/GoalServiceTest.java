package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.goal.jpa.GoalStatus;
import com.office.nadoham.planner.PlannerDto;
import com.office.nadoham.planner.PlannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 목표 만들기 규칙: 첫 주 배치(요일 분산·겹침 표시)와 확정 시 검증·저장
class GoalServiceTest {

    // 2026-11-23(월) 오전 9시 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), com.office.nadoham.config.TimeConfig.ZONE);
    private static final List<Integer> WEEKDAYS = List.of(1, 2, 3, 4, 5);

    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private PlannerService plannerService;
    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);
        plannerService = Mockito.mock(PlannerService.class);
        goalService = new GoalService(goalRepository, goalActivityRepository, plannerService, CLOCK);

        when(goalRepository.save(any(GoalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of());
    }

    private GoalRequest previewRequest(String course, Integer weeklyCount, List<Integer> days) {
        return new GoalRequest(course, null, weeklyCount, 20, "19:30", days, null);
    }

    private GoalRequest startRequest(int weeklyCount, List<GoalRequest.Slot> slots) {
        return new GoalRequest("EXERCISE_HABIT", null, weeklyCount, 20, "19:30", null, slots);
    }

    private GoalRequest.Slot slot(String date) {
        return new GoalRequest.Slot(date, null);
    }

    @Test
    @DisplayName("미리보기: 가능한 요일 사이에 주당 횟수만큼 고르게 배치하고 과정의 첫 주 활동을 쓴다")
    void preview_spreadsActivities() {
        List<ActivityPlan> plans = goalService.previewFirstWeek("alice", previewRequest("EXERCISE_HABIT", 3, WEEKDAYS));

        assertThat(plans).extracting(ActivityPlan::date).containsExactly("2026-11-23", "2026-11-24", "2026-11-26");
        assertThat(plans).extracting(ActivityPlan::startTime).containsOnly("19:30");
        assertThat(plans).extracting(ActivityPlan::title).containsExactly("걷기", "스트레칭 + 걷기", "걷기");
        assertThat(plans).extracting(ActivityPlan::conflict).containsOnly(false);
    }

    @Test
    @DisplayName("미리보기: 같은 시간대에 일정이 있으면 겹침으로 표시하고, 시간대가 다르거나 시간 없는 일정은 표시하지 않는다")
    void preview_marksConflict() {
        PlannerDto overlapping = PlannerDto.builder().title("회식").start_time("19:00").build();
        PlannerDto later = PlannerDto.builder().title("야식").start_time("21:00").build();
        PlannerDto noTime = PlannerDto.builder().title("종일").build();
        when(plannerService.getPlansOn(LocalDate.parse("2026-11-23"), "alice")).thenReturn(List.of(overlapping));
        when(plannerService.getPlansOn(LocalDate.parse("2026-11-24"), "alice")).thenReturn(List.of(later));
        when(plannerService.getPlansOn(LocalDate.parse("2026-11-26"), "alice")).thenReturn(List.of(noTime));

        List<ActivityPlan> plans = goalService.previewFirstWeek("alice", previewRequest("EXERCISE_HABIT", 3, WEEKDAYS));

        assertThat(plans.get(0).conflict()).isTrue();
        assertThat(plans.get(0).conflictTitle()).isEqualTo("회식");
        assertThat(plans.get(1).conflict()).isFalse();
        assertThat(plans.get(2).conflict()).isFalse();
    }

    @Test
    @DisplayName("미리보기: 가능한 요일이 주당 횟수보다 적으면 NOT_ENOUGH_DAYS")
    void preview_notEnoughDays() {
        assertThatThrownBy(() -> goalService.previewFirstWeek("alice", previewRequest("EXERCISE_HABIT", 3, List.of(1, 3))))
                .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_ENOUGH_DAYS"));
    }

    @Test
    @DisplayName("미리보기: 없는 과정·잘못된 횟수·요일·시작일은 각각 코드가 있는 오류")
    void preview_invalidInputs() {
        assertThatCode(previewRequest("NOPE", 3, WEEKDAYS), "INVALID_COURSE");
        assertThatCode(previewRequest("EXERCISE_HABIT", 0, WEEKDAYS), "INVALID_WEEKLY_COUNT");
        assertThatCode(previewRequest("EXERCISE_HABIT", 3, List.of()), "INVALID_DAYS");
        assertThatCode(previewRequest("EXERCISE_HABIT", 3, List.of(1, 8, 3)), "INVALID_DAYS");
        assertThatCode(new GoalRequest("EXERCISE_HABIT", "2026-11-22", 3, 20, "19:30", WEEKDAYS, null), "INVALID_START_DATE");
        assertThatCode(new GoalRequest("EXERCISE_HABIT", "2026-02-31", 3, 20, "19:30", WEEKDAYS, null), "INVALID_DATE");
        assertThatCode(new GoalRequest("EXERCISE_HABIT", null, 3, 4, "19:30", WEEKDAYS, null), "INVALID_MINUTES");
        assertThatCode(new GoalRequest("EXERCISE_HABIT", null, 3, 20, "25:00", WEEKDAYS, null), "INVALID_START_TIME");
        assertThatCode(new GoalRequest("EXERCISE_HABIT", null, 3, 20, null, WEEKDAYS, null), "INVALID_START_TIME");
    }

    private void assertThatCode(GoalRequest request, String code) {
        assertThatThrownBy(() -> goalService.previewFirstWeek("alice", request))
                .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("시작: 목표(4주)와 확정한 첫 주 활동을 날짜순으로 저장하고 활동 내용은 서버가 정한다")
    void start_savesGoalAndActivities() {
        GoalResponse response = goalService.start("alice",
                startRequest(3, List.of(slot("2026-11-26"), slot("2026-11-23"), new GoalRequest.Slot("2026-11-24", "07:00"))));

        ArgumentCaptor<GoalEntity> goalCaptor = ArgumentCaptor.forClass(GoalEntity.class);
        verify(goalRepository).save(goalCaptor.capture());
        GoalEntity goal = goalCaptor.getValue();
        assertThat(goal.getOwnerId()).isEqualTo("alice");
        assertThat(goal.getStartDate()).isEqualTo(LocalDate.parse("2026-11-23"));
        assertThat(goal.getEndDate()).isEqualTo(LocalDate.parse("2026-12-20"));
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GoalActivityEntity>> activityCaptor = ArgumentCaptor.forClass(List.class);
        verify(goalActivityRepository).saveAll(activityCaptor.capture());
        List<GoalActivityEntity> activities = activityCaptor.getValue();
        assertThat(activities).extracting(a -> a.getActDate().toString()).containsExactly("2026-11-23", "2026-11-24", "2026-11-26");
        assertThat(activities).extracting(a -> a.getStartTime().toString()).containsExactly("19:30", "07:00", "19:30");
        assertThat(activities).extracting(GoalActivityEntity::getTitle).containsExactly("걷기", "스트레칭 + 걷기", "걷기");
        assertThat(response.activities()).hasSize(3);
    }

    @Test
    @DisplayName("시작: 활동 수·날짜 범위·중복 날짜가 틀리면 INVALID_SLOTS이고 저장하지 않는다")
    void start_invalidSlots() {
        List<GoalRequest> bad = List.of(
                startRequest(3, List.of(slot("2026-11-23"), slot("2026-11-24"))),
                startRequest(1, List.of(slot("2026-11-22"))),
                startRequest(1, List.of(slot("2026-11-30"))),
                startRequest(2, List.of(slot("2026-11-23"), slot("2026-11-23"))),
                startRequest(1, null));

        for (GoalRequest request : bad) {
            assertThatThrownBy(() -> goalService.start("alice", request))
                    .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_SLOTS"));
        }
        verify(goalRepository, never()).save(any());
        verify(goalActivityRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("시작: 진행 중 목표가 3개면 GOAL_LIMIT, 같은 과정이 진행 중이면 ALREADY_ACTIVE")
    void start_limits() {
        when(goalRepository.countByOwnerIdAndStatus("alice", GoalStatus.ACTIVE)).thenReturn(3L);
        assertThatThrownBy(() -> goalService.start("alice", startRequest(1, List.of(slot("2026-11-23")))))
                .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo("GOAL_LIMIT"));

        when(goalRepository.countByOwnerIdAndStatus("alice", GoalStatus.ACTIVE)).thenReturn(1L);
        when(goalRepository.existsByOwnerIdAndCourseCodeAndStatus("alice", "EXERCISE_HABIT", GoalStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> goalService.start("alice", startRequest(1, List.of(slot("2026-11-23")))))
                .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_ACTIVE"));

        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("미리보기: 일정에 종료 시간이 있으면 그 시간까지만 겹침으로 보고(60분 가정 안 함), 종료 시간 안에 시작하면 겹침이다")
    void preview_usesPlanEndTime() {
        PlannerDto endedEarly = PlannerDto.builder().title("짧은 회의").start_time("19:00").end_time("19:20").build();
        PlannerDto longer = PlannerDto.builder().title("긴 회의").start_time("19:00").end_time("20:00").build();

        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of(endedEarly));
        assertThat(goalService.previewFirstWeek("alice", previewRequest("EXERCISE_HABIT", 1, WEEKDAYS)))
                .extracting(ActivityPlan::conflict).containsOnly(false);

        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of(longer));
        assertThat(goalService.previewFirstWeek("alice", previewRequest("EXERCISE_HABIT", 1, WEEKDAYS)))
                .extracting(ActivityPlan::conflict).containsOnly(true);
    }

}
