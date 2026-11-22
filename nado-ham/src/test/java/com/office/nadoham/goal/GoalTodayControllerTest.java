package com.office.nadoham.goal;

import com.office.nadoham.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 보안 설정 + 오늘 탭 컨트롤러 + 상태 코드 매핑(404·409·400)을 DB 없이 함께 확인한다(서비스는 가짜)
@SpringJUnitWebConfig(classes = {SecurityConfig.class, GoalTodayControllerTest.WebConfig.class})
class GoalTodayControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        GoalService goalService() {
            return Mockito.mock(GoalService.class);
        }

        @Bean
        GoalTodayService goalTodayService() {
            return Mockito.mock(GoalTodayService.class);
        }

        @Bean
        GoalCalendarService goalCalendarService() {
            return Mockito.mock(GoalCalendarService.class);
        }

        @Bean
        GoalRetroService goalRetroService() {
            return Mockito.mock(GoalRetroService.class);
        }

        @Bean
        GoalLifecycleService goalLifecycleService() {
            return Mockito.mock(GoalLifecycleService.class);
        }

        @Bean
        GoalSettingsService goalSettingsService() {
            return Mockito.mock(GoalSettingsService.class);
        }

        @Bean
        CalendarExportService calendarExportService() {
            return Mockito.mock(CalendarExportService.class);
        }

        @Bean
        GoalController goalController(GoalService goalService, GoalTodayService goalTodayService,
                                      GoalCalendarService goalCalendarService, GoalRetroService goalRetroService,
                                      GoalLifecycleService goalLifecycleService, GoalSettingsService goalSettingsService,
                                      CalendarExportService calendarExportService) {
            return new GoalController(goalService, goalTodayService, goalCalendarService, goalRetroService,
                    goalLifecycleService, goalSettingsService, calendarExportService);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private GoalTodayService goalTodayService;
    @Autowired
    private GoalCalendarService goalCalendarService;
    @Autowired
    private GoalRetroService goalRetroService;
    @Autowired
    private GoalLifecycleService goalLifecycleService;
    @Autowired
    private GoalSettingsService goalSettingsService;
    @Autowired
    private CalendarExportService calendarExportService;
    @Autowired
    private GoalService goalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(goalTodayService, goalCalendarService, goalRetroService, goalLifecycleService, goalSettingsService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .build();
    }

    private GoalResponse.ActivityResponse activityResponse(String status) {
        return new GoalResponse.ActivityResponse(7, 1, "2026-11-23", "19:30", 20, "걷기 20분", "20분 걷기", status);
    }

    @Test
    @DisplayName("오늘 탭 데이터는 로그인한 본인 아이디로 조회한다")
    void today_usesPrincipal() throws Exception {
        when(goalTodayService.today("alice")).thenReturn(new TodayResponse("2026-11-23", List.of(), List.of(), null, 0, 0));

        mockMvc.perform(get("/goal/today").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-11-23"))
                .andExpect(jsonPath("$.cards").isEmpty());
    }

    @Test
    @DisplayName("완료·짧게 완료·건너뛰기·옮기기는 200과 바뀐 활동을 돌려준다")
    void actions_ok() throws Exception {
        when(goalTodayService.complete("alice", 7, false)).thenReturn(activityResponse("DONE"));
        when(goalTodayService.complete("alice", 7, true)).thenReturn(activityResponse("SHORT_DONE"));
        when(goalTodayService.skip("alice", 7)).thenReturn(activityResponse("SKIPPED"));
        when(goalTodayService.move(any(), Mockito.eq(7), any())).thenReturn(activityResponse("PLANNED"));

        mockMvc.perform(post("/goal/activity/7/done").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mockMvc.perform(post("/goal/activity/7/short-done").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHORT_DONE"));
        mockMvc.perform(post("/goal/activity/7/skip").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SKIPPED"));
        mockMvc.perform(post("/goal/activity/7/move").with(user("alice").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-25\",\"time\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    @DisplayName("옮기기 후보는 candidates 목록으로 돌려준다")
    void candidates_ok() throws Exception {
        when(goalTodayService.moveCandidates("alice", 7))
                .thenReturn(List.of(new ActivityPlan("2026-11-25", "19:30", 20, "걷기 20분", "20분 걷기", false, null)));

        mockMvc.perform(get("/goal/activity/7/candidates").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].date").value("2026-11-25"));
    }

    @Test
    @DisplayName("없는 활동·남의 활동은 404, 상태가 맞지 않으면 409, 입력 오류는 400 이고 code 를 돌려준다")
    void errors_mapToStatus() throws Exception {
        when(goalTodayService.complete("alice", 404, false)).thenThrow(new GoalException("ACTIVITY_NOT_FOUND", "없는 활동입니다."));
        when(goalTodayService.complete("alice", 409, false)).thenThrow(new GoalException("INVALID_STATE", "이미 처리한 활동입니다."));
        when(goalTodayService.move(any(), Mockito.eq(400), any())).thenThrow(new GoalException("INVALID_MOVE", "범위 밖입니다."));

        mockMvc.perform(post("/goal/activity/404/done").with(user("alice").roles("USER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
        mockMvc.perform(post("/goal/activity/409/done").with(user("alice").roles("USER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(post("/goal/activity/400/move").with(user("alice").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2030-01-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MOVE"));
    }

    @Test
    @DisplayName("USER 가 아니면(비로그인·ADMIN·승인 전 사용자) 오늘 탭 API 를 쓸 수 없고(로그인·접근 거부 화면으로 보내진다) 서비스도 호출되지 않는다")
    void onlyUserRole() throws Exception {
        mockMvc.perform(post("/goal/activity/7/done").with(user("boss").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/goal/activity/7/done").with(user("newbie").roles("PRE_USER")))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/goal/activity/7/done"))
                .andExpect(status().is3xxRedirection());

        verify(goalTodayService, never()).complete(any(), Mockito.anyInt(), Mockito.anyBoolean());
    }

    @Test
    @DisplayName("달력용 목표 활동은 본인 아이디로 조회해 items 로 돌려주고, 범위 밖 기간은 400 INVALID_PERIOD")
    void calendar() throws Exception {
        when(goalCalendarService.monthItems("alice", 2026, 11)).thenReturn(List.of(
                new CalendarItem(7, 1, "EXERCISE_HABIT", "운동", 23, "19:30", 20, "걷기 20분", "PLANNED", false, null)));
        when(goalCalendarService.monthItems("alice", 2031, 1)).thenThrow(new GoalException("INVALID_PERIOD", "범위 밖입니다."));

        mockMvc.perform(get("/goal/calendar?year=2026&month=11").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].day").value(23))
                .andExpect(jsonPath("$.items[0].courseCode").value("EXERCISE_HABIT"));
        mockMvc.perform(get("/goal/calendar?year=2031&month=1").with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        mockMvc.perform(get("/goal/calendar?year=2026&month=11").with(user("boss").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("회고 API: 회고 목록·다음 주 미리보기·확정·마무리는 본인 아이디로 처리한다")
    void retroApis() throws Exception {
        RetroResponse retro = new RetroResponse(1, "운동", "습관", 1, "2026-11-22", 3, 2, 0, 0, 1, false, 3, 20, "19:30", "이번 주 3회 중 2회 실천했어요.");
        when(goalRetroService.pending("alice")).thenReturn(List.of(retro));
        when(goalRetroService.previewNext(Mockito.eq("alice"), Mockito.eq(1), any()))
                .thenReturn(List.of(new ActivityPlan("2026-11-24", "19:30", 20, "빠르게 걷기 20분", "안내", false, null)));
        GoalResponse goalResponse = new GoalResponse(1, "EXERCISE_HABIT", "운동", "습관", "확인", "2026-11-16", "2026-12-13", 3, 20, "19:30", "ACTIVE", List.of());
        when(goalRetroService.startNext(Mockito.eq("alice"), Mockito.eq(1), any())).thenReturn(goalResponse);
        when(goalRetroService.finish("alice", 1)).thenReturn(goalResponse);

        mockMvc.perform(get("/goal/retro").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retros[0].weekNo").value(1))
                .andExpect(jsonPath("$.retros[0].suggestedWeeklyCount").value(3));
        String body = "{\"weekNo\":1,\"weeklyCount\":3,\"minutes\":20,\"startTime\":\"19:30\",\"days\":[1,2,3]}";
        mockMvc.perform(post("/goal/1/next/preview").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activities[0].date").value("2026-11-24"));
        mockMvc.perform(post("/goal/1/next/start").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/goal/1/finish").with(user("alice").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회고 API 오류: 없는·남의 목표는 404, 회고 전·이미 계획함·마지막 주는 409, 입력 오류는 400")
    void retroErrors() throws Exception {
        when(goalRetroService.previewNext(Mockito.eq("alice"), Mockito.eq(404), any())).thenThrow(new GoalException("GOAL_NOT_FOUND", "없는 목표입니다."));
        when(goalRetroService.previewNext(Mockito.eq("alice"), Mockito.eq(409), any())).thenThrow(new GoalException("RETRO_NOT_READY", "아직입니다."));
        when(goalRetroService.startNext(Mockito.eq("alice"), Mockito.eq(410), any())).thenThrow(new GoalException("ALREADY_PLANNED", "이미 계획했습니다."));
        when(goalRetroService.finish("alice", 411)).thenThrow(new GoalException("NO_NEXT_WEEK", "마지막 주입니다."));
        when(goalRetroService.startNext(Mockito.eq("alice"), Mockito.eq(400), any())).thenThrow(new GoalException("INVALID_SLOTS", "활동 수가 다릅니다."));
        String body = "{\"weekNo\":1}";

        mockMvc.perform(post("/goal/404/next/preview").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
        mockMvc.perform(post("/goal/409/next/preview").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETRO_NOT_READY"));
        mockMvc.perform(post("/goal/410/next/start").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_PLANNED"));
        mockMvc.perform(post("/goal/411/finish").with(user("alice").roles("USER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_NEXT_WEEK"));
        mockMvc.perform(post("/goal/400/next/start").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOTS"));
    }

    @Test
    @DisplayName("회고 API 는 USER 가 아니면 쓸 수 없고 서비스도 호출되지 않는다")
    void retroOnlyUser() throws Exception {
        mockMvc.perform(get("/goal/retro").with(user("boss").roles("ADMIN"))).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/goal/1/finish").with(user("newbie").roles("PRE_USER"))).andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/goal/1/next/start")).andExpect(status().is3xxRedirection());

        verify(goalRetroService, never()).finish(any(), Mockito.anyInt());
        verify(goalRetroService, never()).pending(any());
    }

    @Test
    @DisplayName("목표 일시 중지·다시 시작은 본인 아이디로 처리하고 없는 목표 404, 상태·한도 문제는 409·400 으로 알린다")
    void pauseResume() throws Exception {
        GoalResponse paused = new GoalResponse(1, "EXERCISE_HABIT", "운동", "습관", "확인", "2026-11-16", "2026-12-13", 3, 20, "19:30", "PAUSED", List.of());
        when(goalLifecycleService.pause("alice", 1)).thenReturn(paused);
        when(goalLifecycleService.pause("alice", 404)).thenThrow(new GoalException("GOAL_NOT_FOUND", "없는 목표입니다."));
        when(goalLifecycleService.pause("alice", 409)).thenThrow(new GoalException("INVALID_STATE", "진행 중인 목표만 가능합니다."));
        when(goalLifecycleService.resume("alice", 1)).thenReturn(paused);
        when(goalLifecycleService.resume("alice", 400)).thenThrow(new GoalException("GOAL_LIMIT", "한도입니다."));

        mockMvc.perform(post("/goal/1/pause").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(post("/goal/404/pause").with(user("alice").roles("USER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));
        mockMvc.perform(post("/goal/409/pause").with(user("alice").roles("USER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(post("/goal/1/resume").with(user("alice").roles("USER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/goal/400/resume").with(user("alice").roles("USER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOAL_LIMIT"));
        mockMvc.perform(post("/goal/1/pause").with(user("boss").roles("ADMIN"))).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("목표 실행 조건 수정: 본인 아이디로 처리하고, 상태·횟수 문제는 409, 없는 목표 404, 입력 오류 400 으로 알린다")
    void settings() throws Exception {
        GoalResponse goal = new GoalResponse(1, "EXERCISE_HABIT", "운동", "습관", "확인", "2026-11-16", "2026-12-13", 2, 25, "19:30", "ACTIVE", List.of());
        when(goalSettingsService.update(Mockito.eq("alice"), Mockito.eq(1), any())).thenReturn(new SettingsResult(goal, List.of(), 1));
        when(goalSettingsService.update(Mockito.eq("alice"), Mockito.eq(404), any())).thenThrow(new GoalException("GOAL_NOT_FOUND", "없는 목표입니다."));
        when(goalSettingsService.update(Mockito.eq("alice"), Mockito.eq(409), any())).thenThrow(new GoalException("CANNOT_REDUCE", "줄일 수 없습니다."));
        when(goalSettingsService.update(Mockito.eq("alice"), Mockito.eq(410), any())).thenThrow(new GoalException("WEEK_ENDED", "끝난 주입니다."));
        when(goalSettingsService.update(Mockito.eq("alice"), Mockito.eq(400), any())).thenThrow(new GoalException("INVALID_WEEKLY_COUNT", "1~7회입니다."));
        String body = "{\"weeklyCount\":2,\"minutes\":25,\"startTime\":\"19:30\"}";

        mockMvc.perform(put("/goal/1/settings").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.removed").value(1)).andExpect(jsonPath("$.goal.weeklyCount").value(2));
        mockMvc.perform(put("/goal/404/settings").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/goal/409/settings").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_REDUCE"));
        mockMvc.perform(put("/goal/410/settings").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WEEK_ENDED"));
        mockMvc.perform(put("/goal/400/settings").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/goal/1/settings").with(user("boss").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("종료일 수정: 본인 아이디로 처리하고, 입력 오류는 400 으로 알린다")
    void endDate() throws Exception {
        GoalResponse goal = new GoalResponse(1, "EXERCISE_HABIT", "운동", "습관", "확인", "2026-11-16", "2026-12-31", 3, 20, "19:30", "ACTIVE", List.of());
        when(goalSettingsService.changeEndDate(Mockito.eq("alice"), Mockito.eq(1), any())).thenReturn(goal);
        when(goalSettingsService.changeEndDate(Mockito.eq("alice"), Mockito.eq(400), any())).thenThrow(new GoalException("INVALID_END_DATE", "오늘 이후여야 합니다."));
        String body = "{\"endDate\":\"2026-12-31\"}";

        mockMvc.perform(put("/goal/1/end-date").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endDate").value("2026-12-31"));
        mockMvc.perform(put("/goal/400/end-date").with(user("alice").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_END_DATE"));
        mockMvc.perform(put("/goal/1/end-date").with(user("boss").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("캘린더 내보내기: 본인 데이터를 .ics 파일로 내려 주고 관리자는 막힌다")
    void exportCalendar() throws Exception {
        when(calendarExportService.export("alice")).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        mockMvc.perform(get("/goal/calendar.ics").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"nado-ham.ics\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
        mockMvc.perform(get("/goal/calendar.ics").with(user("boss").roles("ADMIN"))).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("과정 목록: 세부 테마와 개인 목표, 고를 수 있는 기간만 담고 주차별 활동 내용은 내려 주지 않는다")
    void courses() throws Exception {
        mockMvc.perform(get("/goal/courses").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].subTheme").exists())
                .andExpect(jsonPath("$[0].months[3]").value(12))
                .andExpect(jsonPath("$[0].weeks").doesNotExist())
                .andExpect(jsonPath("$[0].stages").doesNotExist())
                .andExpect(jsonPath("$[9].personal").value(true));
    }

}
