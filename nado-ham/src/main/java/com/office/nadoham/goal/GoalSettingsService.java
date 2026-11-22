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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 진행 중인 목표의 실행 조건(주당 횟수·1회 소요 시간·시작 시각) 수정.
// 소요 시간·시작 시각은 아직 하지 않은 예정(PLANNED) 활동 전부에 적용하고 이후 주 계획의 기본값이 된다.
// 주당 횟수는 가장 최근 주(지금 진행 중인 주)에 바로 적용한다: 줄이면 아직 오지 않은 예정 활동을 뒤쪽 날짜부터 지우고,
// 늘리면 오늘부터 그 주 끝 사이에서 같은 목표의 활동이 없는 날에 고르게 새 활동을 만든다.
// 이미 끝냈거나 지난 활동은 건드리지 않는다. 같은 요청이 겹쳐도 한 번만 처리되도록 목표 행을 잠근다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalSettingsService {

    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final GoalService goalService;
    private final Clock clock;

    @Transactional
    public SettingsResult update(String ownerId, int goalNo, SettingsRequest request) {
        log.info("update()");

        GoalEntity goal = goalRepository.findWithLockByNo(goalNo).orElse(null);
        if (goal == null || !goal.getOwnerId().equals(ownerId)) {
            throw new GoalException("GOAL_NOT_FOUND", "없는 목표입니다.");
        }
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new GoalException("INVALID_STATE", "진행 중인 목표만 수정할 수 있습니다. 일시 중지한 목표는 먼저 다시 시작해 주세요.");
        }

        int weeklyCount = goalService.requireWeeklyCount(request == null ? null : request.weeklyCount());
        int minutes = goalService.requireMinutes(request.minutes());
        LocalTime time = goalService.requireTime(request.startTime());

        LocalDate today = LocalDate.now(clock);
        List<GoalActivityEntity> activities = new ArrayList<>(
                goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(List.of(goal.getNo())));

        // 새 조건은 아직 하지 않은 예정 활동 전부에 적용한다
        activities.stream().filter(a -> a.getStatus() == ActivityStatus.PLANNED).forEach(a -> a.changeSchedule(time, minutes));

        int removed = adjustCount(goal, activities, weeklyCount, today);
        List<GoalActivityEntity> added = addMissing(goal, activities, weeklyCount, minutes, time, today);

        goal.adjustPlan(weeklyCount, minutes, time);

        List<GoalActivityEntity> all = goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(List.of(goal.getNo()));
        List<ActivityPlan> addedPlans = added.stream().map(a -> toPlan(goal.getOwnerId(), a)).toList();

        return new SettingsResult(goalService.toResponse(goal, all), addedPlans, removed);
    }

    // 이번 주 활동 수가 새 주당 횟수보다 많으면 오늘 이후 예정 활동을 뒤쪽 날짜부터 지운다. 지운 개수를 돌려준다
    private int adjustCount(GoalEntity goal, List<GoalActivityEntity> activities, int weeklyCount, LocalDate today) {
        List<GoalActivityEntity> week = latestWeek(activities);
        int excess = week.size() - weeklyCount;
        if (excess <= 0) {
            return 0;
        }

        List<GoalActivityEntity> removable = week.stream()
                .filter(a -> a.getStatus() == ActivityStatus.PLANNED && !a.getActDate().isBefore(today))
                .sorted(Comparator.comparing(GoalActivityEntity::getActDate).reversed())
                .toList();
        if (removable.size() < excess) {
            throw new GoalException("CANNOT_REDUCE", "이번 주에 이미 끝냈거나 지난 활동이 있어 " + weeklyCount + "회로 줄일 수 없습니다. 다음 주 계획에서 줄여 주세요.");
        }

        List<GoalActivityEntity> toDelete = removable.subList(0, excess);
        goalActivityRepository.deleteAll(toDelete);
        activities.removeAll(toDelete);

        return excess;
    }

    // 이번 주 활동 수가 새 주당 횟수보다 적으면 오늘부터 이번 주 끝까지의 빈 날에 활동을 더한다
    private List<GoalActivityEntity> addMissing(GoalEntity goal, List<GoalActivityEntity> activities, int weeklyCount,
                                                int minutes, LocalTime time, LocalDate today) {
        List<GoalActivityEntity> week = latestWeek(activities);
        int missing = weeklyCount - week.size();
        if (missing <= 0 || week.isEmpty()) {
            return List.of();
        }

        int weekNo = week.get(0).getWeekNo();
        LocalDate weekEnd = weekEndOf(goal, weekNo, week);
        if (weekEnd.isBefore(today)) {
            throw new GoalException("WEEK_ENDED", "이번 주가 이미 끝나 횟수를 늘릴 수 없습니다. 다음 주 계획에서 조정해 주세요.");
        }

        Set<LocalDate> used = activities.stream()
                .filter(a -> a.getStatus() != ActivityStatus.SKIPPED)
                .map(GoalActivityEntity::getActDate)
                .collect(Collectors.toSet());
        List<LocalDate> free = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            if (!used.contains(date)) {
                free.add(date);
            }
        }
        if (free.size() < missing) {
            throw new GoalException("NOT_ENOUGH_DAYS", "이번 주에 활동을 넣을 수 있는 빈 날이 " + free.size() + "일뿐이라 " + weeklyCount + "회로 늘릴 수 없습니다.");
        }

        Course course = CourseCatalog.find(goal.getCourseCode()).orElseThrow();
        List<Course.Session> sessions = course.sessionsOfWeek(weekNo);
        List<GoalActivityEntity> created = new ArrayList<>();
        for (int i = 0; i < missing; i++) {
            LocalDate date = free.get(i * free.size() / missing);          // 빈 날 사이를 고르게 고른다
            Course.Session session = sessions.get((week.size() + i) % sessions.size());
            created.add(GoalActivityEntity.builder().goalNo(goal.getNo()).weekNo(weekNo).actDate(date).startTime(time)
                    .minutes(minutes).title(session.title()).guide(session.guide()).build());
        }
        goalActivityRepository.saveAll(created);
        activities.addAll(created);

        return created;
    }

    // 가장 최근 주(주차 번호가 가장 큰 주)의 활동들
    private List<GoalActivityEntity> latestWeek(List<GoalActivityEntity> activities) {
        int weekNo = activities.stream().mapToInt(GoalActivityEntity::getWeekNo).max().orElse(0);

        return activities.stream().filter(a -> a.getWeekNo() == weekNo).toList();
    }

    // 그 주의 마지막 날: 목표 시작일 기준 7일 단위 끝이거나, 그보다 늦게 옮겨진 활동이 있으면 그 날짜(회고와 같은 기준)
    private LocalDate weekEndOf(GoalEntity goal, int weekNo, List<GoalActivityEntity> week) {
        LocalDate natural = goal.getStartDate().plusDays((long) GoalService.WEEK_DAYS * weekNo - 1);
        LocalDate last = week.stream().map(GoalActivityEntity::getActDate).max(LocalDate::compareTo).orElse(natural);

        return last.isAfter(natural) ? last : natural;
    }

    private ActivityPlan toPlan(String ownerId, GoalActivityEntity activity) {
        PlannerDto conflict = goalService.findConflict(ownerId, activity.getActDate(), activity.getStartTime(), activity.getMinutes());

        return new ActivityPlan(activity.getActDate().toString(), StartTimes.format(activity.getStartTime()), activity.getMinutes(),
                activity.getTitle(), activity.getGuide(), conflict != null, conflict != null ? conflict.getTitle() : null);
    }

}
