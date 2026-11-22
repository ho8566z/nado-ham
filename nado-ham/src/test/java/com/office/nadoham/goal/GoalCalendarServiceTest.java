package com.office.nadoham.goal;

import com.office.nadoham.config.TimeConfig;
import com.office.nadoham.goal.jpa.ActivityStatus;
import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.planner.PlannerDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 일정 달력에 겹쳐 보이는 목표 활동: 그 달 범위·건너뜀 제외·완료 표시·겹침 표시·기간 검증
class GoalCalendarServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), TimeConfig.ZONE);
    private static final String OWNER = "alice";

    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private PlannerService plannerService;
    private GoalCalendarService service;
    private GoalEntity goal;

    @BeforeEach
    void setUp() {
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);
        plannerService = Mockito.mock(PlannerService.class);
        GoalService goalService = new GoalService(goalRepository, goalActivityRepository, plannerService, CLOCK);
        service = new GoalCalendarService(goalRepository, goalActivityRepository, goalService);

        goal = GoalEntity.builder().ownerId(OWNER).courseCode("GUITAR_SONG").startDate(LocalDate.of(2026, 11, 20))
                .endDate(LocalDate.of(2026, 12, 17)).weeklyCount(3).minutes(15).preferredTime(LocalTime.of(19, 30)).build();
        ReflectionTestUtils.setField(goal, "no", 1);
        when(goalRepository.findByOwnerIdOrderByNoDesc(OWNER)).thenReturn(List.of(goal));
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of());
    }

    private GoalActivityEntity activity(int no, LocalDate date, String time, ActivityStatus status) {
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(1).weekNo(1).actDate(date)
                .startTime(LocalTime.parse(time)).minutes(15).title("G·D 코드 전환").guide("연습").build();
        ReflectionTestUtils.setField(entity, "no", no);
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    private void returnActivities(GoalActivityEntity... activities) {
        when(goalActivityRepository.findByGoalNoInAndActDateBetweenOrderByActDateAscStartTimeAsc(
                anyCollection(), Mockito.eq(LocalDate.of(2026, 11, 1)), Mockito.eq(LocalDate.of(2026, 11, 30))))
                .thenReturn(List.of(activities));
    }

    @Test
    @DisplayName("목표가 없으면 빈 목록이고 활동을 조회하지 않는다")
    void noGoals() {
        when(goalRepository.findByOwnerIdOrderByNoDesc("bob")).thenReturn(List.of());

        assertThat(service.monthItems("bob", 2026, 11)).isEmpty();
        Mockito.verifyNoInteractions(goalActivityRepository);
    }

    @Test
    @DisplayName("그 달 1일~말일 범위로 조회하고 일·시각·과정 코드·상태를 담아 돌려준다")
    void mapsMonthItems() {
        returnActivities(activity(1, LocalDate.of(2026, 11, 23), "19:30", ActivityStatus.PLANNED),
                activity(2, LocalDate.of(2026, 11, 25), "07:05", ActivityStatus.DONE));

        List<CalendarItem> items = service.monthItems(OWNER, 2026, 11);

        assertThat(items).extracting(CalendarItem::day).containsExactly(23, 25);
        assertThat(items).extracting(CalendarItem::startTime).containsExactly("19:30", "07:05");
        assertThat(items).extracting(CalendarItem::courseCode).containsOnly("GUITAR_SONG");
        assertThat(items).extracting(CalendarItem::theme).containsOnly("자기개발");
        assertThat(items).extracting(CalendarItem::status).containsExactly("PLANNED", "DONE");
    }

    @Test
    @DisplayName("건너뛴 활동은 달력에 보이지 않는다")
    void hidesSkipped() {
        returnActivities(activity(1, LocalDate.of(2026, 11, 23), "19:30", ActivityStatus.SKIPPED),
                activity(2, LocalDate.of(2026, 11, 24), "19:30", ActivityStatus.SHORT_DONE));

        assertThat(service.monthItems(OWNER, 2026, 11)).extracting(CalendarItem::activityNo).containsExactly(2);
    }

    @Test
    @DisplayName("예정 상태 활동만 같은 시간대 일정과의 겹침을 표시하고, 끝낸 활동은 계산하지 않는다")
    void marksConflictOnlyForPlanned() {
        PlannerDto overlapping = PlannerDto.builder().title("회의").start_time("19:00").build();
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of(overlapping));
        returnActivities(activity(1, LocalDate.of(2026, 11, 23), "19:30", ActivityStatus.PLANNED),
                activity(2, LocalDate.of(2026, 11, 24), "19:30", ActivityStatus.DONE));

        List<CalendarItem> items = service.monthItems(OWNER, 2026, 11);

        assertThat(items.get(0).conflict()).isTrue();
        assertThat(items.get(0).conflictTitle()).isEqualTo("회의");
        assertThat(items.get(1).conflict()).isFalse();
        assertThat(items.get(1).conflictTitle()).isNull();
    }

    @Test
    @DisplayName("2025년 1월 ~ 2030년 12월 밖이거나 월이 1~12가 아니면 INVALID_PERIOD")
    void rejectsInvalidPeriod() {
        for (int[] period : new int[][]{{2024, 12}, {2031, 1}, {2026, 0}, {2026, 13}}) {
            try {
                service.monthItems(OWNER, period[0], period[1]);
                throw new AssertionError("예외가 나야 한다: " + period[0] + "-" + period[1]);
            } catch (GoalException e) {
                assertThat(e.getCode()).isEqualTo("INVALID_PERIOD");
            }
        }
    }

}
