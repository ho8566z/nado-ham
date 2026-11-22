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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// 주말 회고와 다음 주 계획: 한 주가 끝나면 n회 중 m회 실천했는지 보여 주고, 사용자가 다음 주 횟수·시간을 조정해
// 2~4주 차 활동을 만든다. 마지막(4주 차) 회고에서는 과정을 마무리한다.
// 주(週)는 목표 시작일 기준 7일 단위이고, 활동이 그 주 끝을 넘어 옮겨졌으면 마지막 활동 날짜까지를 그 주로 본다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalRetroService {

    private final GoalRepository goalRepository;
    private final GoalActivityRepository goalActivityRepository;
    private final GoalService goalService;
    private final Clock clock;

    // 내 진행 중 목표 중 회고할 차례인 것들
    @Transactional(readOnly = true)
    public List<RetroResponse> pending(String ownerId) {
        log.info("pending()");

        LocalDate today = LocalDate.now(clock);

        return goalRepository.findByOwnerIdAndStatusOrderByNoAsc(ownerId, GoalStatus.ACTIVE).stream()
                .map(goal -> retroOf(goal, activitiesOf(goal), today))
                .flatMap(Optional::stream)
                .toList();
    }

    // 알림 서비스가 쓴다: 진행 중인 그 목표가 회고할 차례인지
    @Transactional(readOnly = true)
    public Optional<RetroResponse> pendingOf(int goalNo) {
        GoalEntity goal = goalRepository.findById(goalNo).orElse(null);
        if (goal == null || goal.getStatus() != GoalStatus.ACTIVE) {
            return Optional.empty();
        }

        return retroOf(goal, activitiesOf(goal), LocalDate.now(clock));
    }

    // 다음 주 계획 미리보기(저장하지 않는다)
    @Transactional(readOnly = true)
    public List<ActivityPlan> previewNext(String ownerId, int goalNo, NextWeekRequest request) {
        log.info("previewNext()");

        GoalEntity goal = findOwnedActive(goalRepository.findById(goalNo).orElse(null), ownerId);
        NextWeek next = nextWeekOf(goal, request == null ? null : request.weekNo());

        int weeklyCount = goalService.requireWeeklyCount(request.weeklyCount());
        int minutes = goalService.requireMinutes(request.minutes());
        LocalTime time = goalService.requireTime(request.startTime());

        return goalService.planWeek(ownerId, next.course(), next.weekNo(), next.windowStart(),
                weeklyCount, minutes, time, goalService.parseDays(request.days()));
    }

    // 다음 주 계획 확정: 사용자가 확인한 날짜·시각으로 그 주 활동을 만든다. 목표 행을 잠가 같은 요청이 겹쳐도 한 번만 만든다
    @Transactional
    public GoalResponse startNext(String ownerId, int goalNo, NextWeekRequest request) {
        log.info("startNext()");

        GoalEntity goal = findOwnedActive(goalRepository.findWithLockByNo(goalNo).orElse(null), ownerId);
        NextWeek next = nextWeekOf(goal, request == null ? null : request.weekNo());

        int weeklyCount = goalService.requireWeeklyCount(request.weeklyCount());
        int minutes = goalService.requireMinutes(request.minutes());
        LocalTime defaultTime = goalService.requireTime(request.startTime());
        List<GoalService.ScheduledSlot> slots = goalService.validateSlots(request.slots(), next.windowStart(), weeklyCount, defaultTime);

        goalService.saveWeekActivities(goal.getNo(), next.course(), next.weekNo(), slots, minutes);
        goal.adjustPlan(weeklyCount, minutes, defaultTime);
        goal.extendEndDate(slots.get(slots.size() - 1).date());

        return goalService.toResponse(goal, activitiesOf(goal));
    }

    // 마지막 주 회고 뒤 과정을 마무리한다
    @Transactional
    public GoalResponse finish(String ownerId, int goalNo) {
        log.info("finish()");

        GoalEntity goal = findOwnedActive(goalRepository.findWithLockByNo(goalNo).orElse(null), ownerId);
        List<GoalActivityEntity> activities = activitiesOf(goal);

        RetroResponse retro = retroOf(goal, activities, LocalDate.now(clock))
                .orElseThrow(() -> new GoalException("RETRO_NOT_READY", "아직 회고할 주가 끝나지 않았습니다."));
        if (!retro.finalWeek()) {
            throw new GoalException("RETRO_NOT_READY", "마지막 주가 끝난 뒤에 마무리할 수 있습니다.");
        }

        goal.complete();

        return goalService.toResponse(goal, activities);
    }

    // 회고할 차례인지 판단하고 결과를 만든다. 가장 최근 주가 끝났을 때만 회고가 있다
    Optional<RetroResponse> retroOf(GoalEntity goal, List<GoalActivityEntity> activities, LocalDate today) {
        if (activities.isEmpty()) {
            return Optional.empty();
        }

        int weekNo = activities.stream().mapToInt(GoalActivityEntity::getWeekNo).max().orElseThrow();
        List<GoalActivityEntity> week = activities.stream().filter(a -> a.getWeekNo() == weekNo).toList();

        LocalDate weekEnd = weekEndOf(goal, weekNo, week);
        if (today.isBefore(weekEnd)) {
            return Optional.empty();
        }

        int done = count(week, ActivityStatus.DONE);
        int shortDone = count(week, ActivityStatus.SHORT_DONE);
        int skipped = count(week, ActivityStatus.SKIPPED);
        int missed = count(week, ActivityStatus.PLANNED);
        boolean finalWeek = weekNo >= CourseCatalog.COURSE_WEEKS;

        int planned = week.size();
        int completed = done + shortDone;
        boolean struggled = completed * 2 < planned;
        int suggestedCount = struggled ? Math.max(1, planned - 1) : planned;
        int suggestedMinutes = struggled ? lighter(goal.getMinutes()) : goal.getMinutes();

        Course course = CourseCatalog.find(goal.getCourseCode()).orElseThrow();

        return Optional.of(new RetroResponse(goal.getNo(), course.theme(), course.name(), weekNo, weekEnd.toString(),
                planned, done, shortDone, skipped, missed, finalWeek, suggestedCount, suggestedMinutes,
                message(planned, completed, shortDone, finalWeek, struggled, suggestedCount, suggestedMinutes)));
    }

    // 그 주의 마지막 날: 목표 시작일 기준 7일 단위 끝이거나, 그보다 늦게 옮겨진 활동이 있으면 그 날짜
    private LocalDate weekEndOf(GoalEntity goal, int weekNo, List<GoalActivityEntity> week) {
        LocalDate natural = goal.getStartDate().plusDays((long) GoalService.WEEK_DAYS * weekNo - 1);
        LocalDate last = week.stream().map(GoalActivityEntity::getActDate).max(LocalDate::compareTo).orElse(natural);

        return last.isAfter(natural) ? last : natural;
    }

    // 다음 주를 만들 수 있는 상태인지 확인하고 그 주차·시작 창·과정을 돌려준다
    private NextWeek nextWeekOf(GoalEntity goal, Integer requestedWeekNo) {
        List<GoalActivityEntity> activities = activitiesOf(goal);

        int latestWeek = activities.stream().mapToInt(GoalActivityEntity::getWeekNo).max().orElse(0);
        if (requestedWeekNo != null && latestWeek > requestedWeekNo) {
            throw new GoalException("ALREADY_PLANNED", "이미 다음 주 계획이 만들어져 있습니다.");
        }

        RetroResponse retro = retroOf(goal, activities, LocalDate.now(clock))
                .orElseThrow(() -> new GoalException("RETRO_NOT_READY", "이번 주가 끝난 뒤에 다음 주 계획을 만들 수 있습니다."));
        if (retro.finalWeek()) {
            throw new GoalException("NO_NEXT_WEEK", "마지막 주입니다. 과정을 마무리해 주세요.");
        }

        // 일찍 열면 그 주의 원래 시작일부터, 늦게 열면 오늘부터 7일 안에서 배치한다
        LocalDate natural = goal.getStartDate().plusDays((long) GoalService.WEEK_DAYS * retro.weekNo());
        LocalDate today = LocalDate.now(clock);
        LocalDate windowStart = natural.isAfter(today) ? natural : today;

        return new NextWeek(retro.weekNo() + 1, windowStart, CourseCatalog.find(goal.getCourseCode()).orElseThrow());
    }

    private GoalEntity findOwnedActive(GoalEntity goal, String ownerId) {
        if (goal == null || !goal.getOwnerId().equals(ownerId)) {
            throw new GoalException("GOAL_NOT_FOUND", "없는 목표입니다.");
        }
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new GoalException("INVALID_STATE", "진행 중인 목표가 아닙니다.");
        }

        return goal;
    }

    private List<GoalActivityEntity> activitiesOf(GoalEntity goal) {
        return goalActivityRepository.findByGoalNoInOrderByActDateAscStartTimeAsc(List.of(goal.getNo()));
    }

    private static int count(List<GoalActivityEntity> week, ActivityStatus status) {
        return (int) week.stream().filter(a -> a.getStatus() == status).count();
    }

    // 분량을 줄인 제안: 지금의 2/3 를 5분 단위로 올림하고 최소 시간 아래로는 내려가지 않는다
    private static int lighter(int minutes) {
        int reduced = (int) Math.ceil(minutes * 2.0 / 3 / 5) * 5;

        return Math.max(GoalService.MIN_MINUTES, Math.min(reduced, minutes));
    }

    private static String message(int planned, int completed, int shortDone, boolean finalWeek, boolean struggled,
                                  int suggestedCount, int suggestedMinutes) {
        StringBuilder text = new StringBuilder("이번 주 " + planned + "회 중 " + completed + "회 실천했어요");
        if (shortDone > 0) {
            text.append("(5분만 하기 ").append(shortDone).append("회 포함)");
        }
        text.append(".");

        if (finalWeek) {
            text.append(completed == 0
                    ? " 괜찮아요. 한 달 동안 시도한 것만으로도 충분해요. 마무리하고 언제든 다시 시작해 볼 수 있어요."
                    : " 한 달 과정을 마쳤어요. 수고했어요!");
        } else if (struggled) {
            text.append(" 괜찮아요. 다음 주는 주 ").append(suggestedCount).append("회, ").append(suggestedMinutes)
                    .append("분으로 가볍게 이어가 볼까요?");
        } else {
            text.append(" 다음 주도 주 ").append(suggestedCount).append("회로 이어갈까요?");
        }

        return text.toString();
    }

    private record NextWeek(int weekNo, LocalDate windowStart, Course course) {
    }

}
