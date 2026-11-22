package com.office.nadoham.goal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    private final GoalTodayService goalTodayService;
    private final GoalCalendarService goalCalendarService;
    private final GoalRetroService goalRetroService;
    private final GoalLifecycleService goalLifecycleService;

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

    // 오늘 탭 데이터
    @GetMapping("/today")
    public ResponseEntity<TodayResponse> today(Principal principal) {
        log.info("today()");

        return ResponseEntity.ok(goalTodayService.today(principal.getName()));
    }

    @GetMapping("/activity/{no}/candidates")
    public ResponseEntity<Map<String, Object>> candidates(@PathVariable("no") int no, Principal principal) {
        log.info("candidates()");

        return ResponseEntity.ok(Map.of("candidates", goalTodayService.moveCandidates(principal.getName(), no)));
    }

    @PostMapping("/activity/{no}/done")
    public ResponseEntity<GoalResponse.ActivityResponse> done(@PathVariable("no") int no, Principal principal) {
        log.info("done()");

        return ResponseEntity.ok(goalTodayService.complete(principal.getName(), no, false));
    }

    @PostMapping("/activity/{no}/short-done")
    public ResponseEntity<GoalResponse.ActivityResponse> shortDone(@PathVariable("no") int no, Principal principal) {
        log.info("shortDone()");

        return ResponseEntity.ok(goalTodayService.complete(principal.getName(), no, true));
    }

    @PostMapping("/activity/{no}/move")
    public ResponseEntity<GoalResponse.ActivityResponse> move(
            @PathVariable("no") int no, @RequestBody ActivityMoveRequest request, Principal principal) {
        log.info("move()");

        return ResponseEntity.ok(goalTodayService.move(principal.getName(), no, request));
    }

    @PostMapping("/activity/{no}/skip")
    public ResponseEntity<GoalResponse.ActivityResponse> skip(@PathVariable("no") int no, Principal principal) {
        log.info("skip()");

        return ResponseEntity.ok(goalTodayService.skip(principal.getName(), no));
    }

    // 일정 달력에 겹쳐 보여 줄 그 달의 목표 활동(읽기 전용)
    @GetMapping("/calendar")
    public ResponseEntity<Map<String, Object>> calendar(
            @RequestParam("year") int year, @RequestParam("month") int month, Principal principal) {
        log.info("calendar()");

        return ResponseEntity.ok(Map.of("items", goalCalendarService.monthItems(principal.getName(), year, month)));
    }

    // 회고할 차례인 목표들(끝난 주의 실천 결과와 다음 주 제안)
    @GetMapping("/retro")
    public ResponseEntity<Map<String, Object>> retro(Principal principal) {
        log.info("retro()");

        return ResponseEntity.ok(Map.of("retros", goalRetroService.pending(principal.getName())));
    }

    @PostMapping("/{goalNo}/next/preview")
    public ResponseEntity<Map<String, Object>> previewNext(
            @PathVariable("goalNo") int goalNo, @RequestBody NextWeekRequest request, Principal principal) {
        log.info("previewNext()");

        return ResponseEntity.ok(Map.of("activities", goalRetroService.previewNext(principal.getName(), goalNo, request)));
    }

    @PostMapping("/{goalNo}/next/start")
    public ResponseEntity<GoalResponse> startNext(
            @PathVariable("goalNo") int goalNo, @RequestBody NextWeekRequest request, Principal principal) {
        log.info("startNext()");

        return ResponseEntity.ok(goalRetroService.startNext(principal.getName(), goalNo, request));
    }

    @PostMapping("/{goalNo}/finish")
    public ResponseEntity<GoalResponse> finish(@PathVariable("goalNo") int goalNo, Principal principal) {
        log.info("finish()");

        return ResponseEntity.ok(goalRetroService.finish(principal.getName(), goalNo));
    }

    // 목표 일시 중지·다시 시작
    @PostMapping("/{goalNo}/pause")
    public ResponseEntity<GoalResponse> pause(@PathVariable("goalNo") int goalNo, Principal principal) {
        log.info("pause()");

        return ResponseEntity.ok(goalLifecycleService.pause(principal.getName(), goalNo));
    }

    @PostMapping("/{goalNo}/resume")
    public ResponseEntity<GoalResponse> resume(@PathVariable("goalNo") int goalNo, Principal principal) {
        log.info("resume()");

        return ResponseEntity.ok(goalLifecycleService.resume(principal.getName(), goalNo));
    }

    @ExceptionHandler(GoalException.class)
    public ResponseEntity<Map<String, Object>> handleGoalException(GoalException e) {
        return ResponseEntity.status(statusOf(e)).body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

    // 남의 활동·목표와 없는 것은 404, 이미 처리했거나 아직 못 하는 상태(회고 전·이미 계획함)는 409, 나머지 입력 오류는 400
    private HttpStatus statusOf(GoalException e) {
        return switch (e.getCode()) {
            case "ACTIVITY_NOT_FOUND", "GOAL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_STATE", "RETRO_NOT_READY", "ALREADY_PLANNED", "NO_NEXT_WEEK" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

}
