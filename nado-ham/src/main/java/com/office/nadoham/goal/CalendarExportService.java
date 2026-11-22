package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.ActivityStatus;
import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.goal.jpa.GoalStatus;
import com.office.nadoham.planner.PlannerDto;
import com.office.nadoham.planner.PlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 외부 캘린더(구글·애플·네이버 등) 가져오기용 iCalendar(.ics) 만들기: 내 일정(PLAN)과 건너뛰지 않은 목표 활동을 내보낸다.
// 시각은 시간대를 붙이지 않는 지역 시각(floating)으로 내보내 가져오는 캘린더의 시간대로 보인다. 일시 중지한 목표의 예정 활동은 날짜가 확정되지 않아 뺀다.
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String CRLF = "\r\n";
    private static final int FOLD_BYTES = 73;                  // 한 줄 75바이트 제한에 맞춘 여유(이어지는 줄은 앞의 공백 1바이트가 붙는다)
    private static final int ASSUMED_PLAN_MINUTES = GoalService.ASSUMED_PLAN_MINUTES;

    private final PlannerService plannerService;
    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public String export(String ownerId) {
        log.info("export()");

        String stamp = LocalDateTime.now(clock).atZone(clock.getZone()).withZoneSameInstant(ZoneOffset.UTC).format(STAMP);
        List<String> lines = new ArrayList<>(List.of("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//nado-ham//Calendar//KO",
                "CALSCALE:GREGORIAN", "X-WR-CALNAME:nado-ham"));

        plannerService.getAllPlans(ownerId).forEach(plan -> lines.addAll(planEvent(plan, stamp)));

        Map<Integer, GoalEntity> goals = goalRepository.findByOwnerIdOrderByNoDesc(ownerId).stream()
                .collect(Collectors.toMap(GoalEntity::getNo, Function.identity()));
        if (!goals.isEmpty()) {
            goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(goals.keySet()).stream()
                    .filter(activity -> activity.getStatus() != ActivityStatus.SKIPPED)
                    .filter(activity -> !(goals.get(activity.getGoalNo()).getStatus() == GoalStatus.PAUSED
                            && activity.getStatus() == ActivityStatus.PLANNED))
                    .forEach(activity -> lines.addAll(activityEvent(activity, goals.get(activity.getGoalNo()), stamp)));
        }

        lines.add("END:VCALENDAR");

        return lines.stream().map(CalendarExportService::fold).collect(Collectors.joining(CRLF)) + CRLF;
    }

    private List<String> planEvent(PlannerDto plan, String stamp) {
        LocalDate date = LocalDate.of(plan.getYear(), plan.getMonth(), plan.getDate());
        List<String> lines = new ArrayList<>(List.of("BEGIN:VEVENT", "UID:plan-" + plan.getNo() + "@nado-ham", "DTSTAMP:" + stamp));

        if (plan.getStart_time() == null) {
            lines.add("DTSTART;VALUE=DATE:" + date.format(DATE));
            lines.add("DTEND;VALUE=DATE:" + date.plusDays(1).format(DATE));
        } else {
            LocalDateTime start = date.atTime(LocalTime.parse(plan.getStart_time()));
            LocalDateTime end = plan.getEnd_time() != null && !LocalTime.parse(plan.getEnd_time()).isBefore(LocalTime.parse(plan.getStart_time()))
                    ? date.atTime(LocalTime.parse(plan.getEnd_time()))
                    : start.plusMinutes(ASSUMED_PLAN_MINUTES);
            lines.add("DTSTART:" + start.format(DATE_TIME));
            lines.add("DTEND:" + end.format(DATE_TIME));
        }
        lines.add("SUMMARY:" + escape(plan.getTitle()));
        if (plan.getBody() != null && !plan.getBody().isBlank()) {
            lines.add("DESCRIPTION:" + escape(plan.getBody()));
        }
        lines.add("END:VEVENT");

        return lines;
    }

    private List<String> activityEvent(GoalActivityEntity activity, GoalEntity goal, String stamp) {
        CoursePlan plan = CoursePlan.of(goal);
        LocalDateTime start = activity.getActDate().atTime(activity.getStartTime());
        boolean finished = activity.getStatus() != ActivityStatus.PLANNED;

        List<String> lines = new ArrayList<>(List.of("BEGIN:VEVENT", "UID:goal-activity-" + activity.getNo() + "@nado-ham",
                "DTSTAMP:" + stamp, "DTSTART:" + start.format(DATE_TIME),
                "DTEND:" + start.plusMinutes(activity.getMinutes()).format(DATE_TIME),
                "SUMMARY:" + escape("[" + plan.course().theme() + "] " + activity.getTitle() + (finished ? " ✓" : "")),
                "DESCRIPTION:" + escape(activity.getGuide())));
        lines.add("END:VEVENT");

        return lines;
    }

    // iCalendar 텍스트 값 이스케이프(\ ; , 줄바꿈)
    static String escape(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    // 한 줄이 75바이트(UTF-8)를 넘으면 CRLF + 공백으로 이어 쓴다. 한글 같은 여러 바이트 글자는 중간에서 자르지 않는다
    static String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= FOLD_BYTES + 2) {
            return line;
        }

        StringBuilder out = new StringBuilder();
        int bytes = 0;
        int limit = FOLD_BYTES + 2;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > limit) {
                out.append(CRLF).append(' ');
                bytes = 1;
                limit = FOLD_BYTES;
            }
            out.appendCodePoint(codePoint);
            bytes += size;
            i += Character.charCount(codePoint);
        }

        return out.toString();
    }

}
