package com.office.nadoham.admin;

import com.office.nadoham.goal.GoalMetrics;
import com.office.nadoham.goal.GoalMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 목표 실행 캘린더 지표 화면(/admin/goal-metrics). /admin/** 는 SecurityConfig 에서 ADMIN 만 들어올 수 있다
@Slf4j
@Controller
@RequestMapping("/admin/goal-metrics")
@RequiredArgsConstructor
public class AdminGoalMetricsController {

    private final GoalMetricsService goalMetricsService;

    @GetMapping({"", "/"})
    public String page() {
        log.info("page()");

        return "admin/goal_metrics";
    }

    // 지표 데이터(JSON). 화면의 JS 가 가져온다
    @GetMapping("/data")
    public ResponseEntity<GoalMetrics> data() {
        log.info("data()");

        return ResponseEntity.ok(goalMetricsService.compute());
    }

}
