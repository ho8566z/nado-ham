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
        GoalController goalController(GoalService goalService, GoalTodayService goalTodayService,
                                      GoalCalendarService goalCalendarService) {
            return new GoalController(goalService, goalTodayService, goalCalendarService);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private GoalTodayService goalTodayService;
    @Autowired
    private GoalCalendarService goalCalendarService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(goalTodayService, goalCalendarService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private GoalResponse.ActivityResponse activityResponse(String status) {
        return new GoalResponse.ActivityResponse(7, 1, "2026-11-23", "19:30", 20, "걷기 20분", "20분 걷기", status);
    }

    @Test
    @DisplayName("오늘 탭 데이터는 로그인한 본인 아이디로 조회한다")
    void today_usesPrincipal() throws Exception {
        when(goalTodayService.today("alice")).thenReturn(new TodayResponse("2026-11-23", List.of(), List.of(), null));

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

}
