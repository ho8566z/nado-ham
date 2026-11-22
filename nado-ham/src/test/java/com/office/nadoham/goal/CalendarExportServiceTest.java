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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

// 외부 캘린더 내보내기(.ics): 일정·목표 활동 변환, 이스케이프, 75바이트 접기
class CalendarExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), TimeConfig.ZONE);

    private PlannerService plannerService;
    private GoalRepository goalRepository;
    private GoalActivityRepository goalActivityRepository;
    private CalendarExportService service;
    private GoalEntity goal;

    @BeforeEach
    void setUp() {
        plannerService = Mockito.mock(PlannerService.class);
        goalRepository = Mockito.mock(GoalRepository.class);
        goalActivityRepository = Mockito.mock(GoalActivityRepository.class);
        service = new CalendarExportService(plannerService, goalRepository, goalActivityRepository, CLOCK);

        goal = GoalEntity.builder().ownerId("alice").courseCode("GUITAR_SONG").startDate(LocalDate.of(2026, 11, 23))
                .endDate(LocalDate.of(2026, 12, 20)).weeklyCount(3).minutes(15).preferredTime(LocalTime.of(19, 30)).build();
        ReflectionTestUtils.setField(goal, "no", 7);
        when(goalRepository.findByOwnerIdOrderByNoDesc("alice")).thenReturn(List.of(goal));
        when(plannerService.getAllPlans("alice")).thenReturn(List.of());
        when(goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(anyCollection())).thenReturn(List.of());
    }

    private GoalActivityEntity activity(int no, ActivityStatus status) {
        GoalActivityEntity entity = GoalActivityEntity.builder().goalNo(7).weekNo(1).actDate(LocalDate.of(2026, 11, 24))
                .startTime(LocalTime.of(19, 30)).minutes(15).title("G·D 코드 전환").guide("코드, 연습; 15분").build();
        ReflectionTestUtils.setField(entity, "no", no);
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    @Test
    @DisplayName("일정: 시간이 있으면 시작·종료 시각, 종료가 없으면 60분, 시간이 없으면 종일 일정으로 내보낸다")
    void exportsPlans() {
        when(plannerService.getAllPlans("alice")).thenReturn(List.of(
                PlannerDto.builder().no(1).year(2026).month(11).date(24).title("회의, 주간").body("줄1\n줄2").start_time("10:00").end_time("11:30").build(),
                PlannerDto.builder().no(2).year(2026).month(11).date(25).title("점심").start_time("12:00").build(),
                PlannerDto.builder().no(3).year(2026).month(11).date(26).title("휴가").build()));

        String ics = service.export("alice");

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("UID:plan-1@nado-ham", "DTSTART:20261124T100000", "DTEND:20261124T113000",
                "SUMMARY:회의\\, 주간", "DESCRIPTION:줄1\\n줄2");
        assertThat(ics).contains("DTSTART:20261125T120000", "DTEND:20261125T130000");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20261126", "DTEND;VALUE=DATE:20261127");
        assertThat(ics).contains("DTSTAMP:20261123T000000Z");
    }

    @Test
    @DisplayName("목표 활동: 건너뛴 활동과 일시 중지한 목표의 예정 활동은 빼고, 끝낸 활동은 ✓ 로 표시한다")
    void exportsGoalActivities() {
        when(goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(anyCollection())).thenReturn(List.of(
                activity(1, ActivityStatus.PLANNED), activity(2, ActivityStatus.DONE), activity(3, ActivityStatus.SKIPPED)));

        String ics = service.export("alice");

        assertThat(ics).contains("UID:goal-activity-1@nado-ham", "DTSTART:20261124T193000", "DTEND:20261124T194500",
                "SUMMARY:[자기개발] G·D 코드 전환\r\n", "SUMMARY:[자기개발] G·D 코드 전환 ✓", "DESCRIPTION:코드\\, 연습\\; 15분");
        assertThat(ics).doesNotContain("goal-activity-3@");

        ReflectionTestUtils.setField(goal, "status", GoalStatus.PAUSED);
        String paused = service.export("alice");
        assertThat(paused).doesNotContain("goal-activity-1@").contains("goal-activity-2@");
    }

    @Test
    @DisplayName("접기: 75바이트를 넘는 줄은 공백으로 이어 쓰고 한글 글자를 중간에서 자르지 않는다")
    void foldsLongLines() {
        String line = "SUMMARY:" + "가나다라마바사".repeat(8);

        String folded = CalendarExportService.fold(line);

        for (String part : folded.split("\r\n")) {
            assertThat(part.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(folded.replace("\r\n ", "")).isEqualTo(line);
        assertThat(CalendarExportService.fold("SHORT")).isEqualTo("SHORT");
    }

}
