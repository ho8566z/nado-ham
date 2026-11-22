package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.ActivityStatus;
import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.goal.jpa.GoalActivityRepository;
import com.office.nadoham.goal.jpa.GoalEntity;
import com.office.nadoham.goal.jpa.GoalRepository;
import com.office.nadoham.goal.jpa.GoalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

// 목표 일시 중지·다시 시작. 중지한 목표는 오늘 탭·알림·회고·달력(예정 활동)에서 빠지고 동시 진행 한도(3개)에도 세지 않는다.
// 다시 시작하면 멈춰 있던 일수만큼 남은 예정 활동과 종료일을 뒤로 밀어, 쉬는 동안 활동이 '놓친 활동'으로 쌓이지 않게 한다.
// 같은 요청이 겹쳐도 한 번만 처리되도록 목표 행을 잠그고 상태를 확인한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalLifecycleService {

    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final GoalService goalService;
    private final Clock clock;

    @Transactional
    public GoalResponse pause(String ownerId, int goalNo) {
        log.info("pause()");

        GoalEntity goal = findOwned(ownerId, goalNo);
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new GoalException("INVALID_STATE", "진행 중인 목표만 일시 중지할 수 있습니다.");
        }

        goal.pause(LocalDate.now(clock));

        return goalService.toResponse(goal, activitiesOf(goal));
    }

    @Transactional
    public GoalResponse resume(String ownerId, int goalNo) {
        log.info("resume()");

        GoalEntity goal = findOwned(ownerId, goalNo);
        if (goal.getStatus() != GoalStatus.PAUSED) {
            throw new GoalException("INVALID_STATE", "일시 중지한 목표만 다시 시작할 수 있습니다.");
        }
        if (goalRepository.countByOwnerIdAndStatus(ownerId, GoalStatus.ACTIVE) >= GoalService.MAX_ACTIVE_GOALS) {
            throw new GoalException("GOAL_LIMIT", "동시에 진행할 수 있는 목표는 " + GoalService.MAX_ACTIVE_GOALS + "개까지입니다.");
        }
        if (goalRepository.existsByOwnerIdAndCourseCodeAndStatus(ownerId, goal.getCourseCode(), GoalStatus.ACTIVE)) {
            throw new GoalException("ALREADY_ACTIVE", "이미 진행 중인 같은 과정이 있습니다.");
        }

        LocalDate pausedAt = goal.getPausedAt();
        long shiftDays = goal.resume(LocalDate.now(clock));

        List<GoalActivityEntity> activities = activitiesOf(goal);
        if (shiftDays > 0 && pausedAt != null) {
            // 멈춘 날 이후로 잡혀 있던 예정 활동만 밀어 낸다(멈추기 전에 이미 놓친 활동·끝낸 활동은 그대로)
            activities.stream()
                    .filter(a -> a.getStatus() == ActivityStatus.PLANNED && !a.getActDate().isBefore(pausedAt))
                    .forEach(a -> a.shiftDate(shiftDays));
        }

        return goalService.toResponse(goal, activities);
    }

    private GoalEntity findOwned(String ownerId, int goalNo) {
        GoalEntity goal = goalRepository.findWithLockByNo(goalNo).orElse(null);
        if (goal == null || !goal.getOwnerId().equals(ownerId)) {
            throw new GoalException("GOAL_NOT_FOUND", "없는 목표입니다.");
        }

        return goal;
    }

    private List<GoalActivityEntity> activitiesOf(GoalEntity goal) {
        return goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(List.of(goal.getNo()));
    }

}
