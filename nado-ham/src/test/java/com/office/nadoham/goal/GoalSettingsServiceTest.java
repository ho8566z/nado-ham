package com.office.nadoham.goal;

import com.office.nadoham.config.TimeConfig;
import com.office.nadoham.goal.jpa.ActivityStatus;
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
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 목표 실행 조건(주당 횟수·소요 시간·시작 시각) 수정: 적용 범위, 횟수 줄이기·늘리기, 오류
class GoalSettingsServiceTest {

    private static final String OWNER = "alice";
    private static final LocalDate START = LocalDate.of(2026, 11, 23);        // 월요일. 1주 차는 11/23 ~ 11/29

    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private PlannerService plannerService;
    private GoalEntity goal;
    private final List<GoalActivityEntity> activities = new ArrayList<>();
    private int nextNo = 1;

    @BeforeEach
    void setUp() {
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);
        plannerService = Mockito.mock(PlannerService.class);

        goal = GoalEntity.builder().ownerId(OWNER).courseCode("EXERCISE_HABIT").startDate(START)
                .endDate(START.plusDays(27)).weeklyCount(3).minutes(20).preferredTime(LocalTime.of(19, 30)).build();
        ReflectionTestUtils.setField(goal, "no", 1);
        when(goalRepository.findWithLockByNo(1)).thenReturn(Optional.of(goal));
        when(goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(anyCollection())).thenAnswer(invocation ->
                activities.stream().sorted((a, b) -> a.getActDate().compareTo(b.getActDate())).toList());
        when(goalActivityRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<GoalActivityEntity> saved = new ArrayList<>();
            for (GoalActivityEntity entity : invocation.<Iterable<GoalActivityEntity>>getArgument(0)) {
                ReflectionTestUtils.setField(entity, "no", nextNo++);
                activities.add(entity);
                saved.add(entity);
            }
            return saved;
        });
        Mockito.doAnswer(invocation -> {
            activities.removeAll(invocation.<Iterable<GoalActivityEntity>>getArgument(0) instanceof List<?> list ? list : List.of());
            return null;
        }).when(goalActivityRepository).deleteAll(anyIterable());
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of());
    }

    private GoalSettingsService on(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(12, 0).atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);
        GoalService goalService = new GoalService(goalRepository, goalActivityRepository, plannerService, clock);
        return new GoalSettingsService(goalRepository, goalActivityRepository, goalService, clock);
    }

    private GoalActivityEntity activity(LocalDate date, ActivityStatus status) {
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(1).weekNo(1).actDate(date)
                .startTime(LocalTime.of(19, 30)).minutes(20).title("t").guide("g").build();
        ReflectionTestUtils.setField(entity, "no", nextNo++);
        ReflectionTestUtils.setField(entity, "status", status);
        activities.add(entity);
        return entity;
    }

    // 화요일(11/24): 월 완료, 수·금 예정
    private void typicalWeek() {
        activity(LocalDate.of(2026, 11, 23), ActivityStatus.DONE);
        activity(LocalDate.of(2026, 11, 25), ActivityStatus.PLANNED);
        activity(LocalDate.of(2026, 11, 27), ActivityStatus.PLANNED);
    }

    private GoalException thrownBy(Runnable action) {
        try {
            action.run();
        } catch (GoalException e) {
            return e;
        }
        throw new AssertionError("GoalException 이 나야 한다");
    }

    private SettingsRequest request(int weeklyCount, int minutes, String time) {
        return new SettingsRequest(weeklyCount, minutes, time);
    }

    @Test
    @DisplayName("소요 시간·시작 시각은 예정 활동에만 적용하고 끝낸 활동은 그대로 두며 목표 기본값도 바뀐다")
    void appliesMinutesAndTimeToPlannedOnly() {
        typicalWeek();

        SettingsResult result = on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(3, 30, "07:15"));

        assertThat(activities.get(0).getMinutes()).isEqualTo(20);
        assertThat(activities.get(0).getStartTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(activities.subList(1, 3)).extracting(GoalActivityEntity::getMinutes).containsOnly(30);
        assertThat(activities.subList(1, 3)).extracting(GoalActivityEntity::getStartTime).containsOnly(LocalTime.of(7, 15));
        assertThat(goal.getMinutes()).isEqualTo(30);
        assertThat(goal.getPreferredTime()).isEqualTo(LocalTime.of(7, 15));
        assertThat(result.removed()).isZero();
        assertThat(result.added()).isEmpty();
        assertThat(result.goal().startTime()).isEqualTo("07:15");
    }

    @Test
    @DisplayName("횟수를 줄이면 오늘 이후 예정 활동을 뒤쪽 날짜부터 지우고, 끝낸 활동과 앞쪽 예정 활동은 남긴다")
    void reducesFromTheEnd() {
        typicalWeek();

        SettingsResult result = on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(2, 20, "19:30"));

        assertThat(result.removed()).isEqualTo(1);
        assertThat(activities).extracting(a -> a.getActDate().toString()).containsExactly("2026-11-23", "2026-11-25");
        assertThat(goal.getWeeklyCount()).isEqualTo(2);
        assertThat(result.goal().weeklyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("끝냈거나 지난 활동 때문에 지울 수 있는 예정 활동이 모자라면 CANNOT_REDUCE 이고 아무것도 바꾸지 않는다")
    void cannotReduceBelowFinished() {
        activity(LocalDate.of(2026, 11, 23), ActivityStatus.DONE);
        activity(LocalDate.of(2026, 11, 24), ActivityStatus.SHORT_DONE);
        activity(LocalDate.of(2026, 11, 27), ActivityStatus.PLANNED);

        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(1, 20, "19:30"))).getCode())
                .isEqualTo("CANNOT_REDUCE");
        assertThat(activities).hasSize(3);
    }

    @Test
    @DisplayName("횟수를 늘리면 오늘부터 그 주 끝 사이 같은 목표 활동이 없는 날에 고르게 새 활동을 만든다(과정의 그 주 활동 이름 사용)")
    void increasesOnFreeDays() {
        typicalWeek();

        SettingsResult result = on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(5, 20, "19:30"));

        // 빈 날: 24, 26, 28, 29 → 2개를 고르게(24, 28)
        assertThat(result.added()).extracting(ActivityPlan::date).containsExactly("2026-11-24", "2026-11-28");
        assertThat(result.added()).extracting(ActivityPlan::title).containsExactly("걷기", "스트레칭 + 걷기");
        assertThat(activities).hasSize(5);
        assertThat(activities.stream().filter(a -> a.getWeekNo() == 1)).hasSize(5);
        assertThat(goal.getWeeklyCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("늘린 활동이 같은 시간대 일정과 겹치면 겹침으로 표시한다")
    void addedActivitiesMarkConflicts() {
        typicalWeek();
        PlannerDto meeting = PlannerDto.builder().title("회의").start_time("19:00").build();
        when(plannerService.getPlansOn(LocalDate.of(2026, 11, 24), OWNER)).thenReturn(List.of(meeting));

        SettingsResult result = on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(4, 20, "19:30"));

        assertThat(result.added()).hasSize(1);
        assertThat(result.added().get(0).date()).isEqualTo("2026-11-24");
        assertThat(result.added().get(0).conflict()).isTrue();
        assertThat(result.added().get(0).conflictTitle()).isEqualTo("회의");
    }

    @Test
    @DisplayName("빈 날이 모자라면 NOT_ENOUGH_DAYS, 이번 주가 이미 끝났으면 WEEK_ENDED 이고 아무것도 만들지 않는다")
    void cannotIncrease() {
        typicalWeek();

        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 28)).update(OWNER, 1, request(6, 20, "19:30"))).getCode())
                .isEqualTo("NOT_ENOUGH_DAYS");
        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 30)).update(OWNER, 1, request(5, 20, "19:30"))).getCode())
                .isEqualTo("WEEK_ENDED");
        assertThat(activities).hasSize(3);
    }

    @Test
    @DisplayName("진행 중인 목표만 수정할 수 있고, 남의 목표·없는 목표는 GOAL_NOT_FOUND 이다")
    void stateAndOwner() {
        typicalWeek();
        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 24)).update("mallory", 1, request(3, 20, "19:30"))).getCode()).isEqualTo("GOAL_NOT_FOUND");
        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 24)).update(OWNER, 99, request(3, 20, "19:30"))).getCode()).isEqualTo("GOAL_NOT_FOUND");

        ReflectionTestUtils.setField(goal, "status", GoalStatus.PAUSED);
        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(3, 20, "19:30"))).getCode()).isEqualTo("INVALID_STATE");
        ReflectionTestUtils.setField(goal, "status", GoalStatus.DONE);
        assertThat(thrownBy(() -> on(LocalDate.of(2026, 11, 24)).update(OWNER, 1, request(3, 20, "19:30"))).getCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("입력 값 검증: 횟수 1~7, 소요 시간 5~120분, 시각 HH:mm 이 아니면 거부하고 아무것도 바꾸지 않는다")
    void validatesInput() {
        typicalWeek();
        GoalSettingsService service = on(LocalDate.of(2026, 11, 24));

        assertThat(thrownBy(() -> service.update(OWNER, 1, new SettingsRequest(0, 20, "19:30"))).getCode()).isEqualTo("INVALID_WEEKLY_COUNT");
        assertThat(thrownBy(() -> service.update(OWNER, 1, new SettingsRequest(8, 20, "19:30"))).getCode()).isEqualTo("INVALID_WEEKLY_COUNT");
        assertThat(thrownBy(() -> service.update(OWNER, 1, new SettingsRequest(3, 3, "19:30"))).getCode()).isEqualTo("INVALID_MINUTES");
        assertThat(thrownBy(() -> service.update(OWNER, 1, new SettingsRequest(3, 20, "25:00"))).getCode()).isEqualTo("INVALID_START_TIME");
        assertThat(thrownBy(() -> service.update(OWNER, 1, new SettingsRequest(null, 20, "19:30"))).getCode()).isEqualTo("INVALID_WEEKLY_COUNT");
        assertThat(goal.getMinutes()).isEqualTo(20);
        assertThat(activities.get(1).getMinutes()).isEqualTo(20);
    }

}
