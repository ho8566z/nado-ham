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
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 주말 회고(n회 중 m회, 다음 주 제안)와 2~4주 차 계획 만들기·과정 마무리 규칙
class GoalRetroServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 16);      // 월요일. 1주 차는 11/16 ~ 11/22
    private static final String OWNER = "alice";
    private static final List<Integer> ALL_DAYS = List.of(1, 2, 3, 4, 5, 6, 7);

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

        when(goalRepository.findById(1)).thenReturn(Optional.of(goal));
        when(goalRepository.findWithLockByNo(1)).thenReturn(Optional.of(goal));
        when(goalRepository.findByOwnerIdAndStatusOrderByNoAsc(OWNER, GoalStatus.ACTIVE)).thenReturn(List.of(goal));
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
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of());
    }

    private GoalRetroService serviceOn(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(12, 0).atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);
        GoalService goalService = new GoalService(goalRepository, goalActivityRepository, plannerService, clock);
        return new GoalRetroService(goalRepository, goalActivityRepository, goalService, clock);
    }

    private GoalActivityEntity activity(int weekNo, LocalDate date, ActivityStatus status) {
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(1).weekNo(weekNo).actDate(date)
                .startTime(LocalTime.of(19, 30)).minutes(20).title("t").guide("g").build();
        ReflectionTestUtils.setField(entity, "no", nextNo++);
        ReflectionTestUtils.setField(entity, "status", status);
        activities.add(entity);
        return entity;
    }

    // 1주 차 활동 3개(월·수·금): 완료 done 개, 5분만 하고 완료 shortDone 개, 건너뜀 skipped 개, 나머지는 예정으로 남는다
    private void firstWeek(int done, int shortDone, int skipped) {
        ActivityStatus[] statuses = new ActivityStatus[3];
        int index = 0;
        for (int i = 0; i < done; i++) {
            statuses[index++] = ActivityStatus.DONE;
        }
        for (int i = 0; i < shortDone; i++) {
            statuses[index++] = ActivityStatus.SHORT_DONE;
        }
        for (int i = 0; i < skipped; i++) {
            statuses[index++] = ActivityStatus.SKIPPED;
        }
        for (; index < 3; index++) {
            statuses[index] = ActivityStatus.PLANNED;
        }
        for (int i = 0; i < 3; i++) {
            activity(1, START.plusDays(i * 2L), statuses[i]);
        }
    }

    private GoalException thrownBy(Runnable action) {
        try {
            action.run();
        } catch (GoalException e) {
            return e;
        }
        throw new AssertionError("GoalException 이 나야 한다");
    }

    private NextWeekRequest previewRequest(Integer weekNo) {
        return new NextWeekRequest(weekNo, 3, 20, "19:30", ALL_DAYS, null);
    }

    @Test
    @DisplayName("회고는 그 주의 마지막 날부터 나온다(그 전에는 없다)")
    void retroAppearsOnWeekEnd() {
        firstWeek(3, 0, 0);

        assertThat(serviceOn(LocalDate.of(2026, 11, 21)).pending(OWNER)).isEmpty();
        assertThat(serviceOn(LocalDate.of(2026, 11, 22)).pending(OWNER)).hasSize(1);
    }

    @Test
    @DisplayName("회고: n회 중 m회와 5분만 하기 횟수를 문구에 담고, 절반 이상 했으면 같은 분량을 제안한다")
    void retroKeepsPlanWhenGoingWell() {
        firstWeek(1, 1, 0);

        RetroResponse retro = serviceOn(LocalDate.of(2026, 11, 22)).pending(OWNER).get(0);

        assertThat(retro).extracting(RetroResponse::weekNo, RetroResponse::planned, RetroResponse::done, RetroResponse::shortDone,
                RetroResponse::missed, RetroResponse::skipped).containsExactly(1, 3, 1, 1, 1, 0);
        assertThat(retro.suggestedWeeklyCount()).isEqualTo(3);
        assertThat(retro.suggestedMinutes()).isEqualTo(20);
        assertThat(retro.finalWeek()).isFalse();
        assertThat(retro.message()).contains("이번 주 3회 중 2회 실천했어요").contains("5분만 하기 1회 포함").contains("다음 주도 주 3회로 이어갈까요?");
    }

    @Test
    @DisplayName("회고: 절반도 못 했으면 횟수를 하나 줄이고 시간을 2/3(5분 단위)로 줄여 가볍게 다시 시작하자고 제안한다")
    void retroLightensPlanWhenStruggling() {
        firstWeek(0, 0, 1);

        RetroResponse retro = serviceOn(LocalDate.of(2026, 11, 22)).pending(OWNER).get(0);

        assertThat(retro.suggestedWeeklyCount()).isEqualTo(2);
        assertThat(retro.suggestedMinutes()).isEqualTo(15);
        assertThat(retro.message()).contains("이번 주 3회 중 0회 실천했어요").contains("괜찮아요").contains("주 2회, 15분");
    }

    @Test
    @DisplayName("회고: 제안 횟수는 1회 아래로, 시간은 5분 아래로 내려가지 않는다")
    void suggestionHasFloor() {
        ReflectionTestUtils.setField(goal, "minutes", 5);
        activity(1, START, ActivityStatus.PLANNED);

        RetroResponse retro = serviceOn(LocalDate.of(2026, 11, 22)).pending(OWNER).get(0);

        assertThat(retro.suggestedWeeklyCount()).isEqualTo(1);
        assertThat(retro.suggestedMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("회고: 활동이 주 끝을 넘어 옮겨졌으면 그 활동 날짜까지를 그 주로 본다")
    void movedActivityExtendsWeek() {
        firstWeek(2, 0, 0);
        activity(1, START.plusDays(8), ActivityStatus.PLANNED);        // 11/24 로 옮겨진 활동

        assertThat(serviceOn(LocalDate.of(2026, 11, 23)).pending(OWNER)).isEmpty();
        assertThat(serviceOn(LocalDate.of(2026, 11, 24)).pending(OWNER)).hasSize(1);
    }

    @Test
    @DisplayName("마지막(4주 차) 회고는 finalWeek 이고 과정 마무리 문구를 쓴다")
    void finalWeekRetro() {
        activity(4, START.plusDays(21), ActivityStatus.DONE);
        activity(4, START.plusDays(23), ActivityStatus.DONE);

        RetroResponse retro = serviceOn(START.plusDays(27)).pending(OWNER).get(0);

        assertThat(retro.finalWeek()).isTrue();
        assertThat(retro.weekNo()).isEqualTo(4);
        assertThat(retro.message()).contains("한 달 과정을 마쳤어요");
    }

    private void makeLongGoal(int months) {
        ReflectionTestUtils.setField(goal, "months", months);
        ReflectionTestUtils.setField(goal, "courseCode", "GUITAR_SONG");
        ReflectionTestUtils.setField(goal, "endDate", START.plusDays(months * 28L - 1));
    }

    @Test
    @DisplayName("3개월 과정: 4주 차 회고는 마지막 주가 아니고 한 달 목표를 돌아보며 다음 달 목표를 알려 준다")
    void longCourseMonthEndRetro() {
        makeLongGoal(3);
        activity(4, START.plusDays(21), ActivityStatus.DONE);
        activity(4, START.plusDays(23), ActivityStatus.DONE);

        RetroResponse retro = serviceOn(START.plusDays(27)).pending(OWNER).get(0);

        assertThat(retro.finalWeek()).isFalse();
        assertThat(retro.message()).contains("1개월 차 목표").contains("다음 달 목표는");
    }

    @Test
    @DisplayName("3개월 과정: 12주 차 회고가 마지막 주이고 3개월 과정 마무리 문구를 쓴다")
    void longCourseFinalRetro() {
        makeLongGoal(3);
        activity(12, START.plusDays(77), ActivityStatus.DONE);
        activity(12, START.plusDays(79), ActivityStatus.DONE);

        RetroResponse retro = serviceOn(START.plusDays(83)).pending(OWNER).get(0);

        assertThat(retro.finalWeek()).isTrue();
        assertThat(retro.message()).contains("3개월 과정을 마쳤어요");
    }

    @Test
    @DisplayName("종료일을 앞당기면 그 종료일 안의 마지막 주가 finalWeek 이다")
    void shortenedEndDateMakesFinalWeek() {
        makeLongGoal(3);
        ReflectionTestUtils.setField(goal, "endDate", START.plusDays(27));       // 4주 차 끝으로 앞당김
        activity(4, START.plusDays(21), ActivityStatus.DONE);
        activity(4, START.plusDays(23), ActivityStatus.DONE);

        assertThat(serviceOn(START.plusDays(27)).pending(OWNER).get(0).finalWeek()).isTrue();
    }

    @Test
    @DisplayName("진행 중이 아닌 목표와 활동이 없는 목표는 회고가 없다")
    void noRetroForInactiveOrEmpty() {
        assertThat(serviceOn(LocalDate.of(2026, 11, 30)).pending(OWNER)).isEmpty();

        firstWeek(3, 0, 0);
        ReflectionTestUtils.setField(goal, "status", GoalStatus.DONE);
        assertThat(serviceOn(LocalDate.of(2026, 11, 30)).pendingOf(1)).isEmpty();
    }

    @Test
    @DisplayName("다음 주 미리보기: 그 주의 원래 시작일(11/23)부터 7일 안에서 2주 차 활동 이름으로 배치한다")
    void previewNext() {
        firstWeek(3, 0, 0);

        List<ActivityPlan> plans = serviceOn(LocalDate.of(2026, 11, 22)).previewNext(OWNER, 1, previewRequest(1));

        assertThat(plans).hasSize(3);
        assertThat(plans).extracting(ActivityPlan::date).allMatch(d -> d.compareTo("2026-11-23") >= 0 && d.compareTo("2026-11-29") <= 0);
        assertThat(plans.get(0).title()).isEqualTo("빠르게 걷기");
    }

    @Test
    @DisplayName("다음 주 미리보기: 늦게 열면 오늘부터 7일 안에서 배치한다")
    void previewNextWhenLate() {
        firstWeek(3, 0, 0);

        List<ActivityPlan> plans = serviceOn(LocalDate.of(2026, 11, 26)).previewNext(OWNER, 1, previewRequest(1));

        assertThat(plans).extracting(ActivityPlan::date).allMatch(d -> d.compareTo("2026-11-26") >= 0 && d.compareTo("2026-12-02") <= 0);
    }

    @Test
    @DisplayName("다음 주 만들기 오류: 주가 안 끝났으면 RETRO_NOT_READY, 마지막 주면 NO_NEXT_WEEK, 남의 목표는 GOAL_NOT_FOUND, 이미 계획했으면 ALREADY_PLANNED")
    void nextWeekErrors() {
        firstWeek(3, 0, 0);

        assertThat(thrownBy(() -> serviceOn(LocalDate.of(2026, 11, 20)).previewNext(OWNER, 1, previewRequest(1))).getCode())
                .isEqualTo("RETRO_NOT_READY");
        assertThat(thrownBy(() -> serviceOn(LocalDate.of(2026, 11, 22)).previewNext("mallory", 1, previewRequest(1))).getCode())
                .isEqualTo("GOAL_NOT_FOUND");
        assertThat(thrownBy(() -> serviceOn(LocalDate.of(2026, 11, 22)).previewNext(OWNER, 99, previewRequest(1))).getCode())
                .isEqualTo("GOAL_NOT_FOUND");

        activity(2, LocalDate.of(2026, 11, 24), ActivityStatus.PLANNED);
        assertThat(thrownBy(() -> serviceOn(LocalDate.of(2026, 11, 25)).previewNext(OWNER, 1, previewRequest(1))).getCode())
                .isEqualTo("ALREADY_PLANNED");

        activities.clear();
        activity(4, START.plusDays(21), ActivityStatus.DONE);
        assertThat(thrownBy(() -> serviceOn(START.plusDays(27)).previewNext(OWNER, 1, previewRequest(4))).getCode())
                .isEqualTo("NO_NEXT_WEEK");
    }

    @Test
    @DisplayName("다음 주 확정: 2주 차 활동을 만들고 목표의 횟수·시간·종료일을 반영한다")
    void startNext() {
        firstWeek(3, 0, 0);
        NextWeekRequest request = new NextWeekRequest(1, 2, 15, "07:00", null,
                List.of(new GoalRequest.Slot("2026-11-24", null), new GoalRequest.Slot("2026-11-28", "08:30")));

        GoalResponse response = serviceOn(LocalDate.of(2026, 11, 22)).startNext(OWNER, 1, request);

        List<GoalActivityEntity> week2 = activities.stream().filter(a -> a.getWeekNo() == 2).toList();
        assertThat(week2).extracting(a -> a.getActDate().toString()).containsExactly("2026-11-24", "2026-11-28");
        assertThat(week2).extracting(a -> a.getStartTime().toString()).containsExactly("07:00", "08:30");
        assertThat(week2).extracting(GoalActivityEntity::getMinutes).containsOnly(15);
        assertThat(week2.get(0).getTitle()).isEqualTo("빠르게 걷기");
        assertThat(goal.getWeeklyCount()).isEqualTo(2);
        assertThat(goal.getMinutes()).isEqualTo(15);
        assertThat(response.activities()).hasSize(5);
    }

    @Test
    @DisplayName("다음 주 확정: 늦게 열어 원래 종료일을 넘는 날짜를 고르면 종료일을 늘린다")
    void startNextExtendsEndDate() {
        firstWeek(3, 0, 0);
        LocalDate originalEnd = goal.getEndDate();
        ReflectionTestUtils.setField(goal, "endDate", LocalDate.of(2026, 11, 28));
        NextWeekRequest request = new NextWeekRequest(1, 1, 20, "19:30", null, List.of(new GoalRequest.Slot("2026-11-30", null)));

        serviceOn(LocalDate.of(2026, 11, 26)).startNext(OWNER, 1, request);

        assertThat(goal.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(originalEnd).isAfter(LocalDate.of(2026, 11, 28));
    }

    @Test
    @DisplayName("다음 주 확정: 활동 수가 횟수와 다르거나 7일 밖이거나 같은 날이 겹치면 INVALID_SLOTS 이고 아무것도 만들지 않는다")
    void startNextRejectsBadSlots() {
        firstWeek(3, 0, 0);
        int before = activities.size();
        GoalRetroService service = serviceOn(LocalDate.of(2026, 11, 22));

        List<NextWeekRequest> bad = List.of(
                new NextWeekRequest(1, 2, 20, "19:30", null, List.of(new GoalRequest.Slot("2026-11-24", null))),
                new NextWeekRequest(1, 1, 20, "19:30", null, List.of(new GoalRequest.Slot("2026-12-10", null))),
                new NextWeekRequest(1, 1, 20, "19:30", null, List.of(new GoalRequest.Slot("2026-11-20", null))),
                new NextWeekRequest(1, 2, 20, "19:30", null,
                        List.of(new GoalRequest.Slot("2026-11-24", null), new GoalRequest.Slot("2026-11-24", "20:00"))));
        for (NextWeekRequest request : bad) {
            assertThat(thrownBy(() -> service.startNext(OWNER, 1, request)).getCode()).isEqualTo("INVALID_SLOTS");
        }

        assertThat(activities).hasSize(before);
    }

    @Test
    @DisplayName("다음 주 확정: 같은 요청을 한 번 더 보내면 이미 계획했다는 ALREADY_PLANNED 로 거부한다")
    void startNextTwice() {
        firstWeek(3, 0, 0);
        NextWeekRequest request = new NextWeekRequest(1, 1, 20, "19:30", null, List.of(new GoalRequest.Slot("2026-11-24", null)));
        GoalRetroService service = serviceOn(LocalDate.of(2026, 11, 24));

        service.startNext(OWNER, 1, request);

        assertThat(thrownBy(() -> service.startNext(OWNER, 1, request)).getCode()).isEqualTo("ALREADY_PLANNED");
        assertThat(activities.stream().filter(a -> a.getWeekNo() == 2)).hasSize(1);
    }

    @Test
    @DisplayName("과정 마무리: 마지막 주가 끝났을 때만 목표를 DONE 으로 바꾸고, 그 전·중간 주에는 RETRO_NOT_READY")
    void finish() {
        firstWeek(3, 0, 0);
        assertThat(thrownBy(() -> serviceOn(LocalDate.of(2026, 11, 22)).finish(OWNER, 1)).getCode()).isEqualTo("RETRO_NOT_READY");

        activity(4, START.plusDays(21), ActivityStatus.DONE);
        assertThat(thrownBy(() -> serviceOn(START.plusDays(26)).finish(OWNER, 1)).getCode()).isEqualTo("RETRO_NOT_READY");
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);

        GoalResponse response = serviceOn(START.plusDays(27)).finish(OWNER, 1);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.DONE);
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(thrownBy(() -> serviceOn(START.plusDays(27)).finish(OWNER, 1)).getCode()).isEqualTo("INVALID_STATE");
        assertThat(thrownBy(() -> serviceOn(START.plusDays(27)).finish("mallory", 1)).getCode()).isEqualTo("GOAL_NOT_FOUND");
    }

}
