package com.office.nadoham.admin;

import com.office.nadoham.config.SecurityConfig;
import com.office.nadoham.goal.GoalMetrics;
import com.office.nadoham.goal.GoalMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 지표 화면·데이터는 ADMIN 만 볼 수 있다(서비스는 가짜)
@SpringJUnitWebConfig(classes = {SecurityConfig.class, AdminGoalMetricsControllerTest.WebConfig.class})
class AdminGoalMetricsControllerTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        GoalMetricsService goalMetricsService() {
            return Mockito.mock(GoalMetricsService.class);
        }

        @Bean
        AdminGoalMetricsController adminGoalMetricsController(GoalMetricsService goalMetricsService) {
            return new AdminGoalMetricsController(goalMetricsService);
        }

    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private GoalMetricsService goalMetricsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(goalMetricsService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("관리자는 지표 데이터를 JSON 으로 받는다(비율은 0~1, 계산 불가는 null)")
    void dataForAdmin() throws Exception {
        when(goalMetricsService.compute()).thenReturn(new GoalMetrics("2026-11-22",
                new GoalMetrics.Recovery(5, 2, 1, 2, 2.0 / 3),
                new GoalMetrics.FirstActivity(6, 2, 2, 2, 0.5),
                new GoalMetrics.Completion(0, 0, null),
                new GoalMetrics.Activities(5, 6, 2, 1, 0.25)));

        mockMvc.perform(get("/admin/goal-metrics/data").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-11-22"))
                .andExpect(jsonPath("$.recovery.missedUsers").value(5))
                .andExpect(jsonPath("$.recovery.rate").value(2.0 / 3))
                .andExpect(jsonPath("$.completion.rate").doesNotExist())
                .andExpect(jsonPath("$.activities.shortShare").value(0.25));
    }

    @Test
    @DisplayName("일반 사용자·승인 전 사용자·비로그인은 지표 화면과 데이터를 볼 수 없고 서비스도 호출되지 않는다")
    void deniedForOthers() throws Exception {
        for (String path : new String[]{"/admin/goal-metrics", "/admin/goal-metrics/data"}) {
            mockMvc.perform(get(path).with(user("alice").roles("USER"))).andExpect(redirectedUrl("/member/access_denied"));
            mockMvc.perform(get(path).with(user("newbie").roles("PRE_USER"))).andExpect(redirectedUrl("/member/access_denied"));
            mockMvc.perform(get(path)).andExpect(redirectedUrl("/member/signin"));
        }

        verify(goalMetricsService, never()).compute();
    }

}
