package com.office.nadoham.goal;

import com.office.nadoham.config.TimeConfig;
import com.office.nadoham.goal.jpa.ActivityStatus;
import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.goal.jpa.GoalStatus;
import com.office.nadoham.planner.PlannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 목표 일시 중지·다시 시작: 상태 검사, 쉰 일수만큼 일정 밀기, 한도·중복 검사
class GoalLifecycleServiceTest {

    private static final String OWNER = "alice";
    private static final LocalDate PAUSE_DAY = LocalDate.of(2026, 11, 20);

    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private GoalEntity goal;
    private final List<GoalActivityEntity> activities = new ArrayList<>();
    private int nextNo = 1;

    @BeforeEach
    void setUp() {
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);

        goal = GoalEntity.builder().ownerId(OWNER).courseCode("EXERCISE_HABIT").startDate(LocalDate.of(2026, 11, 16))
                .endDate(LocalDate.of(2026, 12, 13)).weeklyCount(3).minutes(20).preferredTime(LocalTime.of(19, 30)).build();
        ReflectionTestUtils.setField(goal, "no", 1);
        when(goalRepository.findWithLockByNo(1)).thenReturn(Optional.of(goal));
        when(goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(anyCollection())).thenAnswer(invocation ->
                activities.stream().sorted((a, b) -> a.getActDate().compareTo(b.getActDate())).toList());
    }

    private GoalLifecycleService on(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(12, 0).atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);
        GoalService goalService = new GoalService(goalRepository, goalActivityRepository, Mockito.mock(PlannerService.class), clock);
        return new GoalLifecycleService(goalRepository, goalActivityRepository, goalService, clock);
    }

    private GoalActivityEntity activity(LocalDate date, ActivityStatus status) {
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(1).weekNo(1).actDate(date)
                .startTime(LocalTime.of(19, 30)).minutes(20).title("t").guide("g").build();
        ReflectionTestUtils.setField(entity, "no", nextNo++);
        ReflectionTestUtils.setField(entity, "status", status);
        activities.add(entity);
        return entity;
    }

    private GoalException thrownBy(Runnable action) {
        try {
            action.run();
        } catch (GoalException e) {
            return e;
        }
        throw new AssertionError("GoalException 이 나야 한다");
    }

    @Test
    @DisplayName("진행 중인 목표를 일시 중지하면 PAUSED 가 되고 멈춘 날이 기록된다")
    void pause() {
        GoalResponse response = on(PAUSE_DAY).pause(OWNER, 1);

        assertThat(response.status()).isEqualTo("PAUSED");
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.PAUSED);
        assertThat(goal.getPausedAt()).isEqualTo(PAUSE_DAY);
    }

    @Test
    @DisplayName("중지는 진행 중인 목표만, 재시작은 중지한 목표만 되고 남의 목표·없는 목표는 GOAL_NOT_FOUND")
    void stateAndOwnerChecks() {
        assertThat(thrownBy(() -> on(PAUSE_DAY).resume(OWNER, 1)).getCode()).isEqualTo("INVALID_STATE");
        assertThat(thrownBy(() -> on(PAUSE_DAY).pause("mallory", 1)).getCode()).isEqualTo("GOAL_NOT_FOUND");
        assertThat(thrownBy(() -> on(PAUSE_DAY).resume("mallory", 1)).getCode()).isEqualTo("GOAL_NOT_FOUND");
        assertThat(thrownBy(() -> on(PAUSE_DAY).pause(OWNER, 99)).getCode()).isEqualTo("GOAL_NOT_FOUND");

        on(PAUSE_DAY).pause(OWNER, 1);
        assertThat(thrownBy(() -> on(PAUSE_DAY).pause(OWNER, 1)).getCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("다시 시작하면 쉰 일수(5일)만큼 멈춘 날 이후의 예정 활동과 종료일을 밀고, 끝낸·건너뛴·멈추기 전 놓친 활동은 그대로 둔다")
    void resumeShiftsPlannedActivities() {
        GoalActivityEntity beforePause = activity(LocalDate.of(2026, 11, 18), ActivityStatus.PLANNED);     // 멈추기 전에 이미 놓침
        GoalActivityEntity done = activity(LocalDate.of(2026, 11, 20), ActivityStatus.DONE);
        GoalActivityEntity skipped = activity(LocalDate.of(2026, 11, 21), ActivityStatus.SKIPPED);
        GoalActivityEntity planned = activity(LocalDate.of(2026, 11, 22), ActivityStatus.PLANNED);
        GoalActivityEntity later = activity(LocalDate.of(2026, 11, 25), ActivityStatus.PLANNED);
        on(PAUSE_DAY).pause(OWNER, 1);

        GoalResponse response = on(PAUSE_DAY.plusDays(5)).resume(OWNER, 1);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(goal.getPausedAt()).isNull();
        assertThat(goal.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 18));
        assertThat(planned.getActDate()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(later.getActDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(beforePause.getActDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(done.getActDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(skipped.getActDate()).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    @Test
    @DisplayName("같은 날 중지했다가 바로 다시 시작하면 아무것도 밀리지 않는다")
    void resumeSameDayShiftsNothing() {
        GoalActivityEntity planned = activity(LocalDate.of(2026, 11, 22), ActivityStatus.PLANNED);
        on(PAUSE_DAY).pause(OWNER, 1);

        on(PAUSE_DAY).resume(OWNER, 1);

        assertThat(planned.getActDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(goal.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 13));
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    @DisplayName("다시 시작할 때 진행 중 목표가 이미 3개이면 GOAL_LIMIT, 같은 과정이 진행 중이면 ALREADY_ACTIVE 이고 상태는 그대로다")
    void resumeChecksLimitAndDuplicates() {
        on(PAUSE_DAY).pause(OWNER, 1);

        when(goalRepository.countByOwnerIdAndStatus(OWNER, GoalStatus.ACTIVE)).thenReturn(3L);
        assertThat(thrownBy(() -> on(PAUSE_DAY).resume(OWNER, 1)).getCode()).isEqualTo("GOAL_LIMIT");

        when(goalRepository.countByOwnerIdAndStatus(OWNER, GoalStatus.ACTIVE)).thenReturn(1L);
        when(goalRepository.existsByOwnerIdAndCourseCodeAndStatus(OWNER, "EXERCISE_HABIT", GoalStatus.ACTIVE)).thenReturn(true);
        assertThat(thrownBy(() -> on(PAUSE_DAY).resume(OWNER, 1)).getCode()).isEqualTo("ALREADY_ACTIVE");

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.PAUSED);
        assertThat(goal.getPausedAt()).isEqualTo(PAUSE_DAY);
    }

}
