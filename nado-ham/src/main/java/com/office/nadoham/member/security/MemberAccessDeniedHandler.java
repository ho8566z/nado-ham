package com.office.nadoham.member.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

@Slf4j
public class MemberAccessDeniedHandler implements AccessDeniedHandler {

    static final String CSRF_HEADER = "X-CSRF-TOKEN";

    // handle()은 접근 거부시 실행되는 메서드
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        // CSRF 토큰이 없거나 틀린 요청. 화면의 fetch(토큰 헤더를 붙여 보냄)에는 리다이렉트 대신 403 JSON 으로 알려
        // 응답이 접근 거부 화면 HTML 로 바뀌어 오류로 읽히는 일을 막는다. 일반 폼 요청은 접근 거부 화면으로 보낸다
        if (accessDeniedException instanceof CsrfException && request.getHeader(CSRF_HEADER) != null) {
            log.warn("CSRF 토큰 검증 실패: {} {}", request.getMethod(), request.getRequestURI());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"INVALID_CSRF\",\"message\":\"페이지를 새로고침한 뒤 다시 시도해 주세요.\"}");
            return;
        }

        response.sendRedirect("/member/access_denied");

    }
}
