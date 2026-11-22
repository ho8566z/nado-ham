package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.ActivityStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 일정 달력에 겹쳐 보여 줄 목표 활동. 활동은 PLAN 에 복사하지 않고 읽어서 보여 주기만 한다(두 곳에 저장하면 어긋나기 쉽다).
// 건너뛴 활동은 보이지 않고, 완료한 활동은 완료 표시로 남긴다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalCalendarService {

    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final GoalService goalService;

    @Transactional(readOnly = true)
    public List<CalendarItem> monthItems(String ownerId, int year, int month) {
        log.info("monthItems()");

        if (!PlannerService.isValidStatsPeriod(year, month)) {
            throw new GoalException("INVALID_PERIOD", "달력은 2025년 1월부터 2030년 12월까지 조회할 수 있습니다.");
        }

        Map<Integer, GoalEntity> goalByNo = goalRepository.findByOwnerIdOrderByNoDesc(ownerId).stream()
                .collect(Collectors.toMap(GoalEntity::getNo, Function.identity()));
        if (goalByNo.isEmpty()) {
            return List.of();
        }

        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.plusMonths(1).minusDays(1);

        return goalActivityRepository
                .findByGoalNoInAndActDateBetweenOrderByActDateAscStartTimeAsc(goalByNo.keySet(), first, last).stream()
                .filter(activity -> activity.getStatus() != ActivityStatus.SKIPPED)
                .filter(activity -> !isOnHold(activity, goalByNo.get(activity.getGoalNo())))
                .map(activity -> toItem(ownerId, activity, goalByNo.get(activity.getGoalNo())))
                .toList();
    }

    // 일시 중지한 목표의 예정 활동은 아직 날짜가 확정되지 않은 것이라 달력에 보이지 않는다(끝낸 활동은 기록으로 남는다)
    private boolean isOnHold(GoalActivityEntity activity, GoalEntity goal) {
        return goal.getStatus() == GoalStatus.PAUSED && activity.getStatus() == ActivityStatus.PLANNED;
    }

    private CalendarItem toItem(String ownerId, GoalActivityEntity activity, GoalEntity goal) {
        Course course = CourseCatalog.find(goal.getCourseCode()).orElseThrow();

        PlannerDto conflict = activity.getStatus() == ActivityStatus.PLANNED
                ? goalService.findConflict(ownerId, activity.getActDate(), activity.getStartTime(), activity.getMinutes())
                : null;

        return new CalendarItem(activity.getNo(), goal.getNo(), course.code(), course.theme(),
                activity.getActDate().getDayOfMonth(), StartTimes.format(activity.getStartTime()), activity.getMinutes(),
                activity.getTitle(), activity.getStatus().name(), conflict != null,
                conflict != null ? conflict.getTitle() : null);
    }

}
