package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.goal.jpa.GoalStatus;
import com.office.nadoham.planner.PlannerDto;
import com.office.nadoham.planner.PlannerService;
import com.office.nadoham.planner.util.StartTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 목표 만들기: 과정 선택 → 첫 주 계획 미리보기 → 확정(활동 저장). 계획 규칙(요일 분산, 겹침 표시)은 서버가 정하고
// 화면은 미리보기를 보여 주고 사용자가 옮긴 날짜·시각만 확정 요청에 담는다. 활동 이름·안내는 서버가 과정에서 가져온다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    public static final int MAX_ACTIVE_GOALS = 3;           // 동시에 진행할 수 있는 목표 수(기획안의 '추가로 결정할 항목', 잠정값)
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 120;
    public static final int WEEK_DAYS = 7;
    public static final int FIRST_WEEK = 1;

    // 일정(PLAN)에 종료 시간이 없으면 시작 시간 기준 이 길이만큼 차지하는 것으로 보고 겹침을 판단한다
    public static final int ASSUMED_PLAN_MINUTES = 60;

    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final PlannerService plannerService;
    private final Clock clock;

    // 첫 주 계획 미리보기: 시작일부터 7일 안에서 가능한 요일 중 주당 횟수만큼 고르게 배치한다. 저장하지 않는다.
    @Transactional(readOnly = true)
    public List<ActivityPlan> previewFirstWeek(String ownerId, GoalRequest request) {
        log.info("previewFirstWeek()");

        Course course = findCourse(request.courseCode());
        LocalDate startDate = parseStartDate(request.startDate());
        int weeklyCount = requireWeeklyCount(request.weeklyCount());
        int minutes = requireMinutes(request.minutes());
        LocalTime time = requireTime(request.startTime());

        return planWeek(ownerId, course, FIRST_WEEK, startDate, weeklyCount, minutes, time, parseDays(request.days()));
    }

    // weekNo 주차의 계획 배치: windowStart 부터 7일 안에서 가능한 요일 중 주당 횟수만큼 고르게 고른다(겹치는 일정은 표시만 한다)
    List<ActivityPlan> planWeek(String ownerId, Course course, int weekNo, LocalDate windowStart,
                                int weeklyCount, int minutes, LocalTime time, Set<DayOfWeek> days) {
        List<LocalDate> candidates = new ArrayList<>();
        for (int i = 0; i < WEEK_DAYS; i++) {
            LocalDate date = windowStart.plusDays(i);
            if (days.contains(date.getDayOfWeek())) {
                candidates.add(date);
            }
        }
        if (candidates.size() < weeklyCount) {
            throw new GoalException("NOT_ENOUGH_DAYS", "가능한 요일이 주당 횟수보다 적습니다.");
        }

        List<ActivityPlan> plans = new ArrayList<>();
        List<Course.Session> sessions = course.sessionsOfWeek(weekNo);
        for (int i = 0; i < weeklyCount; i++) {
            LocalDate date = candidates.get(i * candidates.size() / weeklyCount);      // 후보 날짜 사이를 고르게 고른다
            plans.add(toActivityPlan(ownerId, date, time, minutes, sessions.get(i % sessions.size())));
        }

        return plans;
    }

    // 계획 시작: 사용자가 확인한 날짜·시각으로 목표와 첫 주 활동을 저장한다. 겹치는 일정이 있어도 사용자가 확정한 것이면 저장한다.
    @Transactional
    public GoalResponse start(String ownerId, GoalRequest request) {
        log.info("start()");

        Course course = findCourse(request.courseCode());
        LocalDate startDate = parseStartDate(request.startDate());
        int weeklyCount = requireWeeklyCount(request.weeklyCount());
        int minutes = requireMinutes(request.minutes());
        LocalTime defaultTime = requireTime(request.startTime());

        List<ScheduledSlot> scheduled = validateSlots(request.slots(), startDate, weeklyCount, defaultTime);

        if (goalRepository.countByOwnerIdAndStatus(ownerId, GoalStatus.ACTIVE) >= MAX_ACTIVE_GOALS) {
            throw new GoalException("GOAL_LIMIT", "동시에 진행할 수 있는 목표는 " + MAX_ACTIVE_GOALS + "개까지입니다.");
        }
        if (goalRepository.existsByOwnerIdAndCourseCodeAndStatus(ownerId, course.code(), GoalStatus.ACTIVE)) {
            throw new GoalException("ALREADY_ACTIVE", "이미 진행 중인 같은 과정이 있습니다.");
        }

        GoalEntity goal = goalRepository.save(GoalEntity.builder()
                .ownerId(ownerId)
                .courseCode(course.code())
                .startDate(startDate)
                .endDate(startDate.plusDays((long) CourseCatalog.COURSE_WEEKS * WEEK_DAYS - 1))
                .weeklyCount(weeklyCount)
                .minutes(minutes)
                .preferredTime(defaultTime)
                .build());

        List<GoalActivityEntity> activities = saveWeekActivities(goal.getNo(), course, FIRST_WEEK, scheduled, minutes);

        return toResponse(goal, activities);
    }

    // 확정 요청의 날짜·시각 목록 검증: 개수가 주당 횟수와 같고, windowStart 부터 7일 안이며, 같은 날 두 번 넣지 않는다. 날짜순으로 돌려준다
    List<ScheduledSlot> validateSlots(List<GoalRequest.Slot> slots, LocalDate windowStart, int weeklyCount, LocalTime defaultTime) {
        if (slots == null || slots.size() != weeklyCount) {
            throw new GoalException("INVALID_SLOTS", "확정할 활동 수가 주당 횟수와 다릅니다.");
        }

        List<ScheduledSlot> scheduled = new ArrayList<>();
        Set<LocalDate> usedDates = new HashSet<>();
        for (GoalRequest.Slot slot : slots) {
            LocalDate date = parseDate(slot.date());
            if (date.isBefore(windowStart) || date.isAfter(windowStart.plusDays(WEEK_DAYS - 1))) {
                throw new GoalException("INVALID_SLOTS", "활동 날짜는 시작일부터 7일 안이어야 합니다.");
            }
            if (!usedDates.add(date)) {
                throw new GoalException("INVALID_SLOTS", "같은 날에 활동을 두 번 넣을 수 없습니다.");
            }
            LocalTime time = slot.time() == null || slot.time().isBlank() ? defaultTime : requireTime(slot.time());
            scheduled.add(new ScheduledSlot(date, time));
        }
        scheduled.sort(Comparator.comparing(ScheduledSlot::date));

        return scheduled;
    }

    // weekNo 주차의 활동을 과정의 해당 주 활동 이름·안내로 저장한다
    List<GoalActivityEntity> saveWeekActivities(int goalNo, Course course, int weekNo, List<ScheduledSlot> scheduled, int minutes) {
        List<Course.Session> sessions = course.sessionsOfWeek(weekNo);
        List<GoalActivityEntity> activities = new ArrayList<>();
        for (int i = 0; i < scheduled.size(); i++) {
            Course.Session session = sessions.get(i % sessions.size());
            activities.add(GoalActivityEntity.builder()
                    .goalNo(goalNo)
                    .weekNo(weekNo)
                    .actDate(scheduled.get(i).date())
                    .startTime(scheduled.get(i).time())
                    .minutes(minutes)
                    .title(session.title())
                    .guide(session.guide())
                    .build());
        }

        goalActivityRepository.saveAll(activities);

        return activities;
    }

    // 내 목표 목록(최근 것부터)과 각 목표의 활동
    @Transactional(readOnly = true)
    public List<GoalResponse> listMine(String ownerId) {
        log.info("listMine()");

        List<GoalEntity> goals = goalRepository.findByOwnerIdOrderByNoDesc(ownerId);
        if (goals.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<GoalActivityEntity>> byGoal = goalActivityRepository
                .findByGoalNoInOrderByActDateAscStartTimeAsc(goals.stream().map(GoalEntity::getNo).toList())
                .stream()
                .collect(Collectors.groupingBy(GoalActivityEntity::getGoalNo));

        return goals.stream()
                .map(goal -> toResponse(goal, byGoal.getOrDefault(goal.getNo(), List.of())))
                .toList();
    }

    private ActivityPlan toActivityPlan(String ownerId, LocalDate date, LocalTime time, int minutes, Course.Session session) {
        PlannerDto conflict = findConflict(ownerId, date, time, minutes);

        return new ActivityPlan(date.toString(), StartTimes.format(time), minutes, session.title(), session.guide(),
                conflict != null, conflict != null ? conflict.getTitle() : null);
    }

    // 같은 날 시작 시간이 있는 일정 중 활동 시간대와 겹치는 첫 일정(시간 없는 일정은 겹침으로 보지 않는다)
    PlannerDto findConflict(String ownerId, LocalDate date, LocalTime time, int minutes) {
        LocalTime end = time.plusMinutes(minutes);
        boolean wraps = end.isBefore(time);        // 자정을 넘기면 그날 끝까지로 본다

        for (PlannerDto plan : plannerService.getPlansOn(date, ownerId)) {
            if (plan.getStart_time() == null) {
                continue;
            }
            LocalTime planStart = LocalTime.parse(plan.getStart_time());
            // 종료 시간이 있으면 그 시간까지, 없으면 시작 후 ASSUMED_PLAN_MINUTES 동안 차지하는 것으로 본다
            LocalTime planEnd = plan.getEnd_time() != null
                    ? LocalTime.parse(plan.getEnd_time())
                    : planStart.plusMinutes(ASSUMED_PLAN_MINUTES);
            boolean planWraps = planEnd.isBefore(planStart);

            boolean startsBeforeActivityEnds = wraps || planStart.isBefore(end);
            boolean endsAfterActivityStarts = planWraps || planEnd.isAfter(time);
            if (startsBeforeActivityEnds && endsAfterActivityStarts) {
                return plan;
            }
        }

        return null;
    }

    GoalResponse toResponse(GoalEntity goal, List<GoalActivityEntity> activities) {
        Course course = CourseCatalog.find(goal.getCourseCode()).orElseThrow();

        List<GoalResponse.ActivityResponse> activityResponses = activities.stream()
                .map(GoalResponse.ActivityResponse::from)
                .toList();

        return new GoalResponse(goal.getNo(), course.code(), course.theme(), course.name(), course.checkpoint(),
                goal.getStartDate().toString(), goal.getEndDate().toString(), goal.getWeeklyCount(), goal.getMinutes(),
                goal.getStatus().name(), activityResponses);
    }

    Course findCourse(String code) {
        return CourseCatalog.find(code == null ? "" : code)
                .orElseThrow(() -> new GoalException("INVALID_COURSE", "없는 과정입니다."));
    }

    // 시작일: 비우면 오늘. 지난 날짜는 받지 않는다
    private LocalDate parseStartDate(String raw) {
        LocalDate today = LocalDate.now(clock);
        if (raw == null || raw.isBlank()) {
            return today;
        }

        LocalDate date = parseDate(raw);
        if (date.isBefore(today)) {
            throw new GoalException("INVALID_START_DATE", "시작일은 오늘 이후여야 합니다.");
        }

        return date;
    }

    LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new GoalException("INVALID_DATE", "날짜는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    int requireWeeklyCount(Integer weeklyCount) {
        if (weeklyCount == null || weeklyCount < 1 || weeklyCount > WEEK_DAYS) {
            throw new GoalException("INVALID_WEEKLY_COUNT", "주당 횟수는 1~7회입니다.");
        }
        return weeklyCount;
    }

    int requireMinutes(Integer minutes) {
        if (minutes == null || minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new GoalException("INVALID_MINUTES", "1회 소요 시간은 " + MIN_MINUTES + "~" + MAX_MINUTES + "분입니다.");
        }
        return minutes;
    }

    LocalTime requireTime(String raw) {
        try {
            String normalized = StartTimes.normalize(raw);
            if (normalized == null) {
                throw new GoalException("INVALID_START_TIME", "활동 시작 시각이 필요합니다.");
            }
            return StartTimes.toLocalTime(normalized);
        } catch (IllegalArgumentException e) {
            throw new GoalException("INVALID_START_TIME", "시각은 HH:mm 형식이어야 합니다.");
        }
    }

    Set<DayOfWeek> parseDays(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            throw new GoalException("INVALID_DAYS", "가능한 요일을 하나 이상 골라야 합니다.");
        }

        Set<DayOfWeek> result = new HashSet<>();
        for (Integer day : days) {
            if (day == null || day < 1 || day > WEEK_DAYS) {
                throw new GoalException("INVALID_DAYS", "요일은 1(월)~7(일)입니다.");
            }
            result.add(DayOfWeek.of(day));
        }

        return result;
    }

    record ScheduledSlot(LocalDate date, LocalTime time) {
    }

}
