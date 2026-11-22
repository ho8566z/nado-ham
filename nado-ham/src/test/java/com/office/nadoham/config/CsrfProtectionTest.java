package com.office.nadoham.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CSRF 보호가 켜져 있는지: 토큰 없이 데이터를 바꾸는 요청은 막고, 토큰이 있으면 통과하며, 읽기(GET)는 영향이 없다.
// 여기서는 일부러 CSRF 기본 토큰을 붙이지 않는 MockMvc 를 쓴다(다른 테스트는 기본으로 토큰을 붙인다).
@SpringJUnitWebConfig(classes = {SecurityConfig.class, CsrfProtectionTest.WebConfig.class})
class CsrfProtectionTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

    }

    // 보안 규칙(USER 전용)만 지나면 응답하는 테스트용 컨트롤러
    @RestController
    static class ProbeController {

        @GetMapping("/planner/probe")
        String read() {
            return "read";
        }

        @PostMapping("/planner/probe")
        String create() {
            return "created";
        }

        @PutMapping("/goal/probe")
        String update() {
            return "updated";
        }

    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("토큰 없는 POST·PUT 은 막히고(접근 거부 화면으로 이동) 컨트롤러는 실행되지 않는다")
    void withoutTokenIsBlocked() throws Exception {
        mockMvc.perform(post("/planner/probe").with(user("alice").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/access_denied"));
        mockMvc.perform(put("/goal/probe").with(user("alice").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/access_denied"));
    }

    @Test
    @DisplayName("fetch 가 토큰 헤더를 붙였는데 틀리면 리다이렉트 대신 403 과 INVALID_CSRF JSON 을 돌려준다")
    void fetchWithBadTokenGets403Json() throws Exception {
        mockMvc.perform(post("/planner/probe").with(user("alice").roles("USER"))
                        .header("X-CSRF-TOKEN", "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CSRF"));
    }

    @Test
    @DisplayName("올바른 토큰(폼 값 또는 헤더)이 있으면 통과한다")
    void withTokenPasses() throws Exception {
        mockMvc.perform(post("/planner/probe").with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string("created"));
        mockMvc.perform(put("/goal/probe").with(user("alice").roles("USER")).with(csrf().asHeader()))
                .andExpect(status().isOk()).andExpect(content().string("updated"));
    }

    @Test
    @DisplayName("읽기(GET)는 토큰이 없어도 된다")
    void getNeedsNoToken() throws Exception {
        mockMvc.perform(get("/planner/probe").with(user("alice").roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string("read"));
    }

    @Test
    @DisplayName("로그인·회원가입·비밀번호 찾기 POST 도 토큰 없이는 처리되지 않는다")
    void publicFormsNeedToken() throws Exception {
        for (String path : new String[]{"/member/signin_confirm", "/member/signup_confirm", "/member/findpassword_confirm"}) {
            mockMvc.perform(post(path).param("id", "alice").param("pw", "x"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/member/access_denied"));
        }
    }

    @Test
    @DisplayName("로그아웃은 POST 에 토큰이 있을 때만 되고, GET 으로는 로그아웃되지 않는다")
    void logoutIsPostWithToken() throws Exception {
        mockMvc.perform(post("/member/signout_confirm").with(user("alice").roles("USER")))
                .andExpect(redirectedUrl("/member/access_denied"));
        mockMvc.perform(post("/member/signout_confirm").with(user("alice").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/member/signout_confirm").with(user("alice").roles("USER")))
                .andExpect(status().is4xxClientError());
    }

}
