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

// 오늘 탭 규칙: 오늘·놓침·다음 구분, 완료·짧게 완료·옮기기·건너뛰기의 상태 변경과 소유자·상태 검사
class GoalTodayServiceTest {

    // 2026-11-23(월) 오전 9시 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), TimeConfig.ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final String OWNER = "alice";

    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private PlannerService plannerService;
    private GoalTodayService service;

    private GoalEntity goal;
    private final List<GoalActivityEntity> activities = new ArrayList<>();
    private int nextActivityNo = 1;

    @BeforeEach
    void setUp() {
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);
        plannerService = Mockito.mock(PlannerService.class);
        GoalService goalService = new GoalService(goalRepository, goalActivityRepository, plannerService, CLOCK);
        service = new GoalTodayService(goalRepository, goalActivityRepository, goalService, CLOCK);

        goal = newGoal(1, OWNER, TODAY.minusDays(7), TODAY.plusDays(20), GoalStatus.ACTIVE);
        when(goalRepository.findById(1)).thenReturn(Optional.of(goal));
        when(goalRepository.findByOwnerIdAndStatusOrderByNoAsc(OWNER, GoalStatus.ACTIVE)).thenReturn(List.of(goal));
        when(goalActivityRepository.findById(any())).thenAnswer(invocation -> activities.stream()
                .filter(a -> a.getNo() == (int) invocation.getArgument(0)).findFirst());
        when(goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(anyCollection())).thenAnswer(invocation ->
                activities.stream()
                        .sorted((a, b) -> a.getActDate().equals(b.getActDate())
                                ? a.getStartTime().compareTo(b.getStartTime()) : a.getActDate().compareTo(b.getActDate()))
                        .toList());
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of());
    }

    private GoalEntity newGoal(int no, String owner, LocalDate start, LocalDate end, GoalStatus status) {
        GoalEntity entity = GoalEntity.builder().ownerId(owner).courseCode("EXERCISE_HABIT").startDate(start)
                .endDate(end).weeklyCount(3).minutes(20).preferredTime(LocalTime.of(19, 30)).build();
        ReflectionTestUtils.setField(entity, "no", no);
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    private GoalActivityEntity activity(LocalDate date, String time) {
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(1).weekNo(1).actDate(date)
                .startTime(LocalTime.parse(time)).minutes(20).title("걷기 20분").guide("20분 걷기").build();
        ReflectionTestUtils.setField(entity, "no", nextActivityNo++);
        activities.add(entity);
        return entity;
    }

    private GoalActivityEntity activityWithStatus(LocalDate date, String time, ActivityStatus status) {
        GoalActivityEntity entity = activity(date, time);
        ReflectionTestUtils.setField(entity, "status", status);
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
    @DisplayName("진행 중인 목표가 없으면 오늘 탭은 비어 있다")
    void today_noGoals() {
        when(goalRepository.findByOwnerIdAndStatusOrderByNoAsc("bob", GoalStatus.ACTIVE)).thenReturn(List.of());

        TodayResponse response = service.today("bob");

        assertThat(response.today()).isEqualTo("2026-11-23");
        assertThat(response.cards()).isEmpty();
        assertThat(response.missed()).isEmpty();
        assertThat(response.next()).isNull();
    }

    @Test
    @DisplayName("오늘 탭: 오늘 활동은 시각순, 지난 예정은 놓침, 가장 가까운 앞날은 next, 건너뛴 활동은 보이지 않는다")
    void today_splitsCardsMissedAndNext() {
        activityWithStatus(TODAY.minusDays(2), "19:30", ActivityStatus.DONE);
        activity(TODAY.minusDays(1), "19:30");
        activity(TODAY, "20:00");
        activityWithStatus(TODAY, "08:00", ActivityStatus.DONE);
        activityWithStatus(TODAY, "12:00", ActivityStatus.SKIPPED);
        activity(TODAY.plusDays(3), "19:30");
        activity(TODAY.plusDays(2), "19:30");

        TodayResponse response = service.today(OWNER);

        assertThat(response.cards()).extracting(TodayResponse.Card::startTime).containsExactly("08:00", "20:00");
        assertThat(response.cards()).extracting(TodayResponse.Card::status).containsExactly("DONE", "PLANNED");
        assertThat(response.missed()).extracting(TodayResponse.Card::date).containsExactly("2026-11-22");
        assertThat(response.next().date()).isEqualTo("2026-11-25");
    }

    @Test
    @DisplayName("오늘 탭: 이번 주 진행도는 같은 주차에서 완료·짧게 완료한 횟수를 센다")
    void today_weeklyProgress() {
        activityWithStatus(TODAY.minusDays(2), "19:30", ActivityStatus.DONE);
        activityWithStatus(TODAY.minusDays(1), "19:30", ActivityStatus.SHORT_DONE);
        activityWithStatus(TODAY.minusDays(3), "19:30", ActivityStatus.SKIPPED);
        activity(TODAY, "19:30");

        TodayResponse.Card card = service.today(OWNER).cards().get(0);

        assertThat(card.weeklyCount()).isEqualTo(3);
        assertThat(card.weeklyDone()).isEqualTo(2);
        assertThat(card.shortGuide()).isEqualTo(CourseCatalog.find("EXERCISE_HABIT").orElseThrow().shortGuide());
    }

    @Test
    @DisplayName("완료하면 DONE, 5분만 하고 완료하면 SHORT_DONE 으로 구분해 저장하고 완료 시각을 남긴다")
    void complete_marksStatus() {
        GoalActivityEntity full = activity(TODAY, "19:30");
        GoalActivityEntity shortOne = activity(TODAY, "20:30");

        assertThat(service.complete(OWNER, full.getNo(), false).status()).isEqualTo("DONE");
        assertThat(service.complete(OWNER, shortOne.getNo(), true).status()).isEqualTo("SHORT_DONE");
        assertThat(full.getDoneAt()).isNotNull();
        assertThat(full.getMissedDate()).isNull();
    }

    @Test
    @DisplayName("예정일이 지난 활동을 짧게 완료하면 처음 놓친 날을 기록한다")
    void complete_recordsMissedDate() {
        GoalActivityEntity overdue = activity(TODAY.minusDays(2), "19:30");

        service.complete(OWNER, overdue.getNo(), true);

        assertThat(overdue.getMissedDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(overdue.getStatus()).isEqualTo(ActivityStatus.SHORT_DONE);
    }

    @Test
    @DisplayName("아직 예정일이 아닌 활동·이미 처리한 활동은 409 상태(INVALID_STATE)로 거부한다")
    void complete_rejectsInvalidState() {
        GoalActivityEntity future = activity(TODAY.plusDays(1), "19:30");
        GoalActivityEntity done = activityWithStatus(TODAY, "19:30", ActivityStatus.DONE);

        assertThat(thrownBy(() -> service.complete(OWNER, future.getNo(), false)).getCode()).isEqualTo("INVALID_STATE");
        assertThat(thrownBy(() -> service.complete(OWNER, done.getNo(), false)).getCode()).isEqualTo("INVALID_STATE");
        assertThat(future.getStatus()).isEqualTo(ActivityStatus.PLANNED);
    }

    @Test
    @DisplayName("남의 활동과 없는 활동은 똑같이 ACTIVITY_NOT_FOUND 이고 상태가 바뀌지 않는다")
    void ownerCheck() {
        GoalActivityEntity mine = activity(TODAY, "19:30");

        assertThat(thrownBy(() -> service.complete("mallory", mine.getNo(), false)).getCode()).isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(thrownBy(() -> service.skip("mallory", mine.getNo())).getCode()).isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(thrownBy(() -> service.move("mallory", mine.getNo(), new ActivityMoveRequest("2026-11-25", null))).getCode())
                .isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(thrownBy(() -> service.moveCandidates("mallory", mine.getNo())).getCode()).isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(thrownBy(() -> service.complete(OWNER, 999, false)).getCode()).isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(mine.getStatus()).isEqualTo(ActivityStatus.PLANNED);
    }

    @Test
    @DisplayName("진행 중이 아닌 목표의 활동은 바꿀 수 없다")
    void inactiveGoal() {
        GoalEntity paused = newGoal(2, OWNER, TODAY.minusDays(7), TODAY.plusDays(20), GoalStatus.PAUSED);
        when(goalRepository.findById(2)).thenReturn(Optional.of(paused));
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(2).weekNo(1).actDate(TODAY)
                .startTime(LocalTime.of(19, 30)).minutes(20).title("t").guide("g").build();
        ReflectionTestUtils.setField(entity, "no", 50);
        activities.add(entity);

        assertThat(thrownBy(() -> service.complete(OWNER, 50, false)).getCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("옮기기: 날짜를 바꾸고 시각을 비우면 원래 시각을 유지하며, 상태는 예정 그대로다")
    void move_changesDateAndKeepsTime() {
        GoalActivityEntity entity = activity(TODAY, "19:30");

        GoalResponse.ActivityResponse response = service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-11-25", null));

        assertThat(response.date()).isEqualTo("2026-11-25");
        assertThat(response.startTime()).isEqualTo("19:30");
        assertThat(entity.getStatus()).isEqualTo(ActivityStatus.PLANNED);
        assertThat(entity.getMissedDate()).isNull();
    }

    @Test
    @DisplayName("옮기기: 시각을 보내면 그 시각으로 바꾸고, 형식이 틀리면 INVALID_START_TIME")
    void move_changesTime() {
        GoalActivityEntity entity = activity(TODAY, "19:30");

        assertThat(service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-11-24", "07:15")).startTime()).isEqualTo("07:15");
        assertThat(thrownBy(() -> service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-11-25", "25:00"))).getCode())
                .isEqualTo("INVALID_START_TIME");
    }

    @Test
    @DisplayName("옮기기: 지난 날짜·목표 기간 밖·같은 목표의 다른 활동이 있는 날·날짜 형식 오류는 거부한다")
    void move_rejectsInvalidTargets() {
        GoalActivityEntity entity = activity(TODAY, "19:30");
        activity(TODAY.plusDays(2), "19:30");

        assertThat(thrownBy(() -> service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-11-22", null))).getCode())
                .isEqualTo("INVALID_MOVE");
        assertThat(thrownBy(() -> service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-12-30", null))).getCode())
                .isEqualTo("INVALID_MOVE");
        assertThat(thrownBy(() -> service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-11-25", null))).getCode())
                .isEqualTo("DATE_TAKEN");
        assertThat(thrownBy(() -> service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-02-31", null))).getCode())
                .isEqualTo("INVALID_DATE");
        assertThat(thrownBy(() -> service.move(OWNER, entity.getNo(), null)).getCode()).isEqualTo("INVALID_DATE");
        assertThat(entity.getActDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("건너뛴 활동이 있던 날은 다시 쓸 수 있다")
    void move_allowsDateOfSkippedActivity() {
        GoalActivityEntity entity = activity(TODAY, "19:30");
        activityWithStatus(TODAY.plusDays(2), "19:30", ActivityStatus.SKIPPED);

        assertThat(service.move(OWNER, entity.getNo(), new ActivityMoveRequest("2026-11-25", null)).date()).isEqualTo("2026-11-25");
    }

    @Test
    @DisplayName("예정일이 지난 활동을 옮기면 처음 놓친 날을 기록하고 예정 상태로 둔다")
    void move_overdueRecordsMissedDate() {
        GoalActivityEntity overdue = activity(TODAY.minusDays(1), "19:30");

        service.move(OWNER, overdue.getNo(), new ActivityMoveRequest("2026-11-24", null));

        assertThat(overdue.getMissedDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(overdue.getStatus()).isEqualTo(ActivityStatus.PLANNED);
    }

    @Test
    @DisplayName("이미 놓친 날을 한 번 기록하면 다시 옮겨도 처음 놓친 날을 유지한다")
    void move_keepsFirstMissedDate() {
        GoalActivityEntity overdue = activity(TODAY.minusDays(3), "19:30");
        service.move(OWNER, overdue.getNo(), new ActivityMoveRequest("2026-11-23", null));
        ReflectionTestUtils.setField(overdue, "actDate", TODAY.minusDays(1));

        service.move(OWNER, overdue.getNo(), new ActivityMoveRequest("2026-11-25", null));

        assertThat(overdue.getMissedDate()).isEqualTo(TODAY.minusDays(3));
    }

    @Test
    @DisplayName("건너뛰기: 오늘·지난 활동만 SKIPPED 로 바꾸고 놓친 날을 기록한다")
    void skip_marksSkipped() {
        GoalActivityEntity overdue = activity(TODAY.minusDays(1), "19:30");
        GoalActivityEntity todays = activity(TODAY, "19:30");
        GoalActivityEntity future = activity(TODAY.plusDays(1), "19:30");

        assertThat(service.skip(OWNER, overdue.getNo()).status()).isEqualTo("SKIPPED");
        assertThat(service.skip(OWNER, todays.getNo()).status()).isEqualTo("SKIPPED");
        assertThat(overdue.getMissedDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(todays.getMissedDate()).isEqualTo(TODAY);
        assertThat(thrownBy(() -> service.skip(OWNER, future.getNo())).getCode()).isEqualTo("INVALID_STATE");
        assertThat(thrownBy(() -> service.skip(OWNER, overdue.getNo())).getCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("옮기기 후보: 오늘부터 7일 안에서 원래 날짜·같은 목표 활동이 있는 날·목표 기간 밖·이미 지난 오늘 시각을 뺀다")
    void candidates_excludesUnavailableDays() {
        GoalActivityEntity entity = activity(TODAY.plusDays(1), "19:30");
        activity(TODAY.plusDays(3), "19:30");
        ReflectionTestUtils.setField(goal, "endDate", TODAY.plusDays(5));

        List<ActivityPlan> candidates = service.moveCandidates(OWNER, entity.getNo());

        assertThat(candidates).extracting(ActivityPlan::date)
                .containsExactly("2026-11-23", "2026-11-25", "2026-11-27", "2026-11-28");
        assertThat(candidates).extracting(ActivityPlan::startTime).containsOnly("19:30");
    }

    @Test
    @DisplayName("옮기기 후보: 같은 시간대 일정이 있는 날은 겹침으로 표시한다")
    void candidates_marksConflict() {
        GoalActivityEntity entity = activity(TODAY.minusDays(1), "19:30");
        PlannerDto overlapping = PlannerDto.builder().title("회의").start_time("19:00").build();
        when(plannerService.getPlansOn(TODAY.plusDays(1), OWNER)).thenReturn(List.of(overlapping));

        List<ActivityPlan> candidates = service.moveCandidates(OWNER, entity.getNo());

        ActivityPlan tomorrow = candidates.stream().filter(c -> c.date().equals("2026-11-24")).findFirst().orElseThrow();
        assertThat(tomorrow.conflict()).isTrue();
        assertThat(tomorrow.conflictTitle()).isEqualTo("회의");
        assertThat(candidates.stream().filter(c -> !c.date().equals("2026-11-24"))).allMatch(c -> !c.conflict());
    }

    @Test
    @DisplayName("옮기기 후보: 오늘 시각이 이미 지났으면 오늘은 후보에 넣지 않는다(오전 9시 기준 08:30)")
    void candidates_skipsPastTimeToday() {
        GoalActivityEntity entity = activity(TODAY.minusDays(1), "08:30");

        assertThat(service.moveCandidates(OWNER, entity.getNo())).extracting(ActivityPlan::date).doesNotContain("2026-11-23");
    }

}
