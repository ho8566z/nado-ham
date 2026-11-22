package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
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

// 3·6·12개월 과정, 현재 수준, 개인 목표를 만들 때의 검증과 저장
class GoalLongCourseTest {

    // 2026-11-23(월) 오전 9시 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), com.office.nadoham.config.TimeConfig.ZONE);
    private static final List<Integer> WEEKDAYS = List.of(1, 2, 3, 4, 5);

    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);
        PlannerService plannerService = Mockito.mock(PlannerService.class);
        goalService = new GoalService(goalRepository, goalActivityRepository, plannerService, CLOCK);

        when(goalRepository.save(any(GoalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(plannerService.getPlansOn(any(), anyString())).thenReturn(List.of());
    }

    private GoalRequest preview(String course, Integer months, String level, String personalGoal) {
        return new GoalRequest(course, null, 3, 20, "19:30", WEEKDAYS, null, months, level, personalGoal);
    }

    private GoalRequest start(String course, Integer months, String level, String personalGoal, List<GoalRequest.Slot> slots) {
        return new GoalRequest(course, null, slots.size(), 20, "19:30", null, slots, months, level, personalGoal);
    }

    private void assertCode(GoalRequest request, String code) {
        assertThatThrownBy(() -> goalService.previewFirstWeek("alice", request))
                .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("미리보기: 기간은 1·3·6·12개월만, 수준은 처음 시작·해 본 적 있음만 받는다")
    void preview_validatesMonthsAndLevel() {
        assertCode(preview("GUITAR_SONG", 2, null, null), "INVALID_MONTHS");
        assertCode(preview("GUITAR_SONG", 24, null, null), "INVALID_MONTHS");
        assertCode(preview("GUITAR_SONG", 3, "EXPERT", null), "INVALID_LEVEL");

        assertThat(goalService.previewFirstWeek("alice", preview("GUITAR_SONG", null, null, null))).hasSize(3);
        assertThat(goalService.previewFirstWeek("alice", preview("GUITAR_SONG", 12, "EXPERIENCED", null))).hasSize(3);
    }

    @Test
    @DisplayName("미리보기: 3개월 첫 주는 첫 주 활동 앞에 익히는 주 안내가 붙는다")
    void preview_longCourseFirstWeek() {
        List<ActivityPlan> plans = goalService.previewFirstWeek("alice", preview("GUITAR_SONG", 3, null, null));

        assertThat(plans.get(0).guide()).startsWith("이번 주는 익히는 주예요. ");
    }

    @Test
    @DisplayName("요약: 기간·월별 중간 목표·끝났을 때 확인할 결과를 돌려주고, 분량이 기본의 2/3 에 못 미치면 안내를 붙인다")
    void summarize_describesPlan() {
        GoalService.PlanSummary summary = goalService.summarize(preview("GUITAR_SONG", 6, null, null));

        assertThat(summary.months()).isEqualTo(6);
        assertThat(summary.totalWeeks()).isEqualTo(24);
        assertThat(summary.milestones()).hasSize(6);
        assertThat(summary.checkpoint()).startsWith("마지막 달(6개월 차) 목표: ");
        assertThat(summary.note()).isNull();

        GoalRequest light = new GoalRequest("GUITAR_SONG", null, 2, 10, "19:30", WEEKDAYS, null, 3, null, null);
        assertThat(goalService.summarize(light).note()).contains("기본(주 3회 15분)보다 적어요");
    }

    @Test
    @DisplayName("시작: 3개월 과정은 종료일이 12주 뒤이고 기간·수준이 목표에 저장된다")
    void start_longCourseSavesMonthsAndLevel() {
        goalService.start("alice", start("GUITAR_SONG", 3, "EXPERIENCED", null, List.of(new GoalRequest.Slot("2026-11-23", null))));

        ArgumentCaptor<GoalEntity> captor = ArgumentCaptor.forClass(GoalEntity.class);
        verify(goalRepository).save(captor.capture());
        GoalEntity goal = captor.getValue();
        assertThat(goal.getMonths()).isEqualTo(3);
        assertThat(goal.getLevel()).isEqualTo("EXPERIENCED");
        assertThat(goal.getEndDate()).isEqualTo(LocalDate.parse("2026-11-23").plusDays(83));
    }

    @Test
    @DisplayName("응답: 월별 중간 목표에 지난 달·지금 달·앞으로 올 달 표시와 기간이 담긴다")
    void response_includesMilestones() {
        GoalResponse response = goalService.start("alice",
                start("TRAVEL_ENGLISH", 6, null, null, List.of(new GoalRequest.Slot("2026-11-23", null))));

        assertThat(response.months()).isEqualTo(6);
        assertThat(response.totalWeeks()).isEqualTo(24);
        assertThat(response.currentMonth()).isEqualTo(1);
        assertThat(response.milestones()).extracting(GoalResponse.MilestoneResponse::state)
                .containsExactly("CURRENT", "UPCOMING", "UPCOMING", "UPCOMING", "UPCOMING", "UPCOMING");
        assertThat(response.subTheme()).isEqualTo("언어(영어)");
    }

    @Test
    @DisplayName("개인 목표: 목표 문장이 필요하고 2~60자 한 문장이어야 한다")
    void personal_requiresGoalSentence() {
        assertCode(preview("PERSONAL_EXERCISE", 1, null, null), "INVALID_PERSONAL_GOAL");
        assertCode(preview("PERSONAL_EXERCISE", 1, null, " "), "INVALID_PERSONAL_GOAL");
        assertCode(preview("PERSONAL_EXERCISE", 1, null, "가"), "INVALID_PERSONAL_GOAL");
        assertCode(preview("PERSONAL_EXERCISE", 1, null, "가".repeat(61)), "INVALID_PERSONAL_GOAL");
        assertCode(preview("PERSONAL_EXERCISE", 1, null, "줄\n바꿈"), "INVALID_PERSONAL_GOAL");

        List<ActivityPlan> plans = goalService.previewFirstWeek("alice", preview("PERSONAL_EXERCISE", 1, null, "  10km 완주하기 "));
        assertThat(plans).allSatisfy(plan -> assertThat(plan.title()).contains("10km 완주하기"));
    }

    @Test
    @DisplayName("개인 목표 시작: 사용자가 고친 활동 이름을 저장하고, 다른 과정은 보낸 이름을 무시한다")
    void personal_savesEditedTitle() {
        goalService.start("alice", start("PERSONAL_STUDY", 1, null, "토익 800점", List.of(
                new GoalRequest.Slot("2026-11-23", null, "단어 30개 외우기"), new GoalRequest.Slot("2026-11-24", null, null))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GoalActivityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(goalActivityRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(GoalActivityEntity::getTitle)
                .containsExactly("단어 30개 외우기", "토익 800점 — 준비하기");

        Mockito.reset(goalActivityRepository);
        goalService.start("alice", start("GUITAR_SONG", 1, null, null, List.of(new GoalRequest.Slot("2026-11-23", null, "바꾼 이름"))));
        verify(goalActivityRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getTitle()).isEqualTo("G·D 코드 전환");
    }

    @Test
    @DisplayName("개인 목표 시작: 활동 이름이 60자를 넘으면 INVALID_TITLE 이고 저장하지 않는다")
    void personal_rejectsLongTitle() {
        assertThatThrownBy(() -> goalService.start("alice", start("PERSONAL_STUDY", 1, null, "토익 800점",
                List.of(new GoalRequest.Slot("2026-11-23", null, "가".repeat(61))))))
                .isInstanceOfSatisfying(GoalException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_TITLE"));
        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("개인 목표는 같은 과정이 진행 중이어도 다른 목표 문장으로 또 만들 수 있다")
    void personal_allowsSameCourseTwice() {
        when(goalRepository.existsByOwnerIdAndCourseCodeAndStatus(anyString(), anyString(), any())).thenReturn(true);

        goalService.start("alice", start("PERSONAL_GROWTH", 1, null, "그림 그리기", List.of(new GoalRequest.Slot("2026-11-23", null))));

        verify(goalRepository).save(any(GoalEntity.class));
    }

}
