package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.ActivityStatus;
import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.goal.jpa.GoalStatus;
import com.office.nadoham.planner.PlannerDto;
import com.office.nadoham.planner.util.StartTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 오늘 탭: 오늘 할 활동·놓친 활동·다음 예정 활동을 보여 주고, 완료·5분만 하기·다른 날로 옮기기·건너뛰기를 기록한다.
// 모든 요청은 로그인한 본인의 활동만 대상이며, 남의 활동 번호는 없는 활동과 똑같이 404 로 답한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalTodayService {

    public static final int CANDIDATE_DAYS = 7;         // 옮기기 후보는 오늘부터 이 일수 안에서 제시한다

    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final GoalService goalService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TodayResponse today(String ownerId) {
        log.info("today()");

        LocalDate today = LocalDate.now(clock);
        List<GoalEntity> goals = goalRepository.findByOwnerIdAndStatusOrderByNoAsc(ownerId, GoalStatus.ACTIVE);
        int pausedGoals = (int) goalRepository.countByOwnerIdAndStatus(ownerId, GoalStatus.PAUSED);
        if (goals.isEmpty()) {
            return new TodayResponse(today.toString(), List.of(), List.of(), null, 0, pausedGoals);
        }

        Map<Integer, GoalEntity> goalByNo = goals.stream().collect(Collectors.toMap(GoalEntity::getNo, Function.identity()));
        List<GoalActivityEntity> activities = goalActivityRepository
                .findByGoalNoInOrderByActDateAscStartTimeAsc(goalByNo.keySet());

        List<TodayResponse.Card> cards = new ArrayList<>();
        List<TodayResponse.Card> missed = new ArrayList<>();
        TodayResponse.Card next = null;
        for (GoalActivityEntity activity : activities) {
            if (activity.getStatus() == ActivityStatus.SKIPPED) {
                continue;
            }
            boolean planned = activity.getStatus() == ActivityStatus.PLANNED;
            LocalDate date = activity.getActDate();

            if (date.isEqual(today)) {
                cards.add(toCard(activity, goalByNo.get(activity.getGoalNo()), activities));
            } else if (planned && date.isBefore(today)) {
                missed.add(toCard(activity, goalByNo.get(activity.getGoalNo()), activities));
            } else if (planned && next == null) {       // 날짜·시각순으로 정렬돼 있어 처음 만나는 것이 가장 가깝다
                next = toCard(activity, goalByNo.get(activity.getGoalNo()), activities);
            }
        }

        return new TodayResponse(today.toString(), cards, missed, next, goals.size(), pausedGoals);
    }

    // 옮기기 후보: 오늘부터 7일 안에서 같은 목표의 다른 활동이 없는 날을 원래 시각으로 제시한다(겹치는 일정은 표시만 한다)
    @Transactional(readOnly = true)
    public List<ActivityPlan> moveCandidates(String ownerId, int activityNo) {
        log.info("moveCandidates()");

        OwnedActivity owned = findOwned(ownerId, activityNo);
        GoalActivityEntity activity = owned.activity();
        requirePlanned(activity);

        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        List<ActivityPlan> candidates = new ArrayList<>();
        for (int i = 0; i < CANDIDATE_DAYS; i++) {
            LocalDate date = today.plusDays(i);
            boolean tooLate = date.isAfter(owned.goal().getEndDate());
            boolean sameDay = date.isEqual(activity.getActDate());
            boolean pastToday = i == 0 && !activity.getStartTime().isAfter(now);      // 오늘이면 아직 안 지난 시각만
            if (tooLate || sameDay || pastToday || isDateTaken(owned, date)) {
                continue;
            }

            PlannerDto conflict = goalService.findConflict(ownerId, date, activity.getStartTime(), activity.getMinutes());
            candidates.add(new ActivityPlan(date.toString(), StartTimes.format(activity.getStartTime()), activity.getMinutes(),
                    activity.getTitle(), activity.getGuide(), conflict != null, conflict != null ? conflict.getTitle() : null));
        }

        return candidates;
    }

    // 완료 기록. shortVersion 이면 '5분만 하기'를 실제로 하고 끝낸 것이라 SHORT_DONE 으로 구분해 저장한다
    @Transactional
    public GoalResponse.ActivityResponse complete(String ownerId, int activityNo, boolean shortVersion) {
        log.info("complete()");

        GoalActivityEntity activity = findOwned(ownerId, activityNo).activity();
        requirePlanned(activity);

        LocalDate today = LocalDate.now(clock);
        if (activity.getActDate().isAfter(today)) {
            throw new GoalException("INVALID_STATE", "아직 예정일이 되지 않은 활동입니다.");
        }
        activity.complete(shortVersion, today, LocalDateTime.now(clock));

        return GoalResponse.ActivityResponse.from(activity);
    }

    // 같은 활동의 날짜·시각만 바꾼다. 시각을 비우면 원래 시각을 유지한다
    @Transactional
    public GoalResponse.ActivityResponse move(String ownerId, int activityNo, ActivityMoveRequest request) {
        log.info("move()");

        OwnedActivity owned = findOwned(ownerId, activityNo);
        GoalActivityEntity activity = owned.activity();
        requirePlanned(activity);

        LocalDate today = LocalDate.now(clock);
        LocalDate date = parseDate(request == null ? null : request.date());
        if (date.isBefore(today) || date.isAfter(owned.goal().getEndDate())) {
            throw new GoalException("INVALID_MOVE", "오늘부터 목표 기간 안의 날짜로만 옮길 수 있습니다.");
        }
        if (isDateTaken(owned, date)) {
            throw new GoalException("DATE_TAKEN", "그날에는 같은 목표의 다른 활동이 있습니다.");
        }
        LocalTime time = parseTime(request.time(), activity.getStartTime());

        activity.reschedule(date, time, today);

        return GoalResponse.ActivityResponse.from(activity);
    }

    // 예정일이 오늘이거나 지난 활동을 이번에는 하지 않기로 한다
    @Transactional
    public GoalResponse.ActivityResponse skip(String ownerId, int activityNo) {
        log.info("skip()");

        GoalActivityEntity activity = findOwned(ownerId, activityNo).activity();
        requirePlanned(activity);

        LocalDate today = LocalDate.now(clock);
        if (activity.getActDate().isAfter(today)) {
            throw new GoalException("INVALID_STATE", "아직 예정일이 되지 않은 활동입니다.");
        }
        activity.skip(today);

        return GoalResponse.ActivityResponse.from(activity);
    }

    private OwnedActivity findOwned(String ownerId, int activityNo) {
        GoalActivityEntity activity = goalActivityRepository.findById(activityNo)
                .orElseThrow(() -> new GoalException("ACTIVITY_NOT_FOUND", "없는 활동입니다."));
        GoalEntity goal = goalRepository.findById(activity.getGoalNo()).orElse(null);
        if (goal == null || !goal.getOwnerId().equals(ownerId)) {
            throw new GoalException("ACTIVITY_NOT_FOUND", "없는 활동입니다.");
        }
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new GoalException("INVALID_STATE", "진행 중인 목표의 활동이 아닙니다.");
        }

        return new OwnedActivity(goal, activity);
    }

    private void requirePlanned(GoalActivityEntity activity) {
        if (activity.getStatus() != ActivityStatus.PLANNED) {
            throw new GoalException("INVALID_STATE", "이미 처리한 활동입니다.");
        }
    }

    // 그 날짜에 같은 목표의 다른 활동(건너뛴 것 제외)이 있는지. 같은 날 활동을 두 번 넣지 않는 첫 주 규칙과 맞춘다
    private boolean isDateTaken(OwnedActivity owned, LocalDate date) {
        return goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(List.of(owned.goal().getNo())).stream()
                .anyMatch(other -> other.getNo() != owned.activity().getNo()
                        && other.getStatus() != ActivityStatus.SKIPPED
                        && other.getActDate().isEqual(date));
    }

    private TodayResponse.Card toCard(GoalActivityEntity activity, GoalEntity goal, List<GoalActivityEntity> all) {
        CoursePlan plan = CoursePlan.of(goal);
        Course course = plan.course();
        int weeklyDone = (int) all.stream()
                .filter(other -> other.getGoalNo() == goal.getNo() && other.getWeekNo() == activity.getWeekNo())
                .filter(other -> other.getStatus() == ActivityStatus.DONE || other.getStatus() == ActivityStatus.SHORT_DONE)
                .count();

        // 분모는 그 주에 계획한 활동 수다(주마다 횟수를 조정할 수 있어 목표의 현재 횟수와 다를 수 있다)
        int weeklyCount = (int) all.stream()
                .filter(other -> other.getGoalNo() == goal.getNo() && other.getWeekNo() == activity.getWeekNo())
                .count();

        return new TodayResponse.Card(activity.getNo(), goal.getNo(), course.theme(), plan.displayName(),
                activity.getActDate().toString(), StartTimes.format(activity.getStartTime()), activity.getMinutes(),
                activity.getTitle(), activity.getGuide(), course.shortGuide(), activity.getStatus().name(),
                weeklyCount, weeklyDone);
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new GoalException("INVALID_DATE", "날짜는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        try {
            String normalized = StartTimes.normalize(raw);
            return normalized == null ? fallback : StartTimes.toLocalTime(normalized);
        } catch (IllegalArgumentException e) {
            throw new GoalException("INVALID_START_TIME", "시각은 HH:mm 형식이어야 합니다.");
        }
    }

    private record OwnedActivity(GoalEntity goal, GoalActivityEntity activity) {
    }

}
