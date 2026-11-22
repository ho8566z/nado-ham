package com.office.nadoham.goal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;
import java.util.List;
import java.util.Map;

// 목표 화면과 데이터. 모든 요청은 로그인한 본인 기준이며 소유자 값은 요청에서 받지 않는다.
@Slf4j
@Controller
@RequestMapping("/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping({"", "/"})
    public String home() {
        log.info("home()");

        return "goal/home";
    }

    // 선택할 수 있는 과정 목록
    @GetMapping("/courses")
    public ResponseEntity<List<Course>> courses() {
        log.info("courses()");

        return ResponseEntity.ok(CourseCatalog.all());
    }

    @PostMapping("/plan/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody GoalRequest request, Principal principal) {
        log.info("preview()");

        return ResponseEntity.ok(Map.of("activities", goalService.previewFirstWeek(principal.getName(), request)));
    }

    @PostMapping("/start")
    public ResponseEntity<GoalResponse> start(@RequestBody GoalRequest request, Principal principal) {
        log.info("start()");

        return ResponseEntity.ok(goalService.start(principal.getName(), request));
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(Principal principal) {
        log.info("list()");

        return ResponseEntity.ok(Map.of("goals", goalService.listMine(principal.getName())));
    }

    @ExceptionHandler(GoalException.class)
    public ResponseEntity<Map<String, Object>> handleGoalException(GoalException e) {
        return ResponseEntity.badRequest().body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

}
