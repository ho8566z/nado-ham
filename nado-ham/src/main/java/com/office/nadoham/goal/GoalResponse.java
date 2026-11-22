package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.planner.util.StartTimes;

import java.util.List;

// 내 목표 한 건과 그 활동들. courseName 은 개인 목표면 사용자가 쓴 목표 문장이다.
// months 는 기간(1·3·6·12), currentMonth 는 지금 달(1부터), milestones 는 월별 중간 목표(지난 달·지금 달·앞으로 올 달 표시).
public record GoalResponse(
        int no,
        String courseCode,
        String theme,
        String subTheme,
        String courseName,
        String checkpoint,
        String startDate,
        String endDate,
        int weeklyCount,
        int minutes,
        String startTime,
        String status,
        int months,
        String level,
        String personalGoal,
        int currentMonth,
        int totalWeeks,
        List<MilestoneResponse> milestones,
        List<ActivityResponse> activities) {

    // 1개월 과정·옛 호출을 위한 짧은 생성자
    public GoalResponse(int no, String courseCode, String theme, String courseName, String checkpoint, String startDate,
                        String endDate, int weeklyCount, int minutes, String startTime, String status,
                        List<ActivityResponse> activities) {
        this(no, courseCode, theme, null, courseName, checkpoint, startDate, endDate, weeklyCount, minutes, startTime,
                status, 1, GoalLevel.BEGINNER.name(), null, 1, CoursePlan.WEEKS_PER_MONTH, List.of(), activities);
    }

    // state: DONE(지난 달), CURRENT(지금 달), UPCOMING(앞으로 올 달)
    public record MilestoneResponse(int month, String name, String goal, String state) {

        public static MilestoneResponse of(CoursePlan.Milestone milestone, int currentMonth) {
            String state = milestone.month() < currentMonth ? "DONE" : milestone.month() == currentMonth ? "CURRENT" : "UPCOMING";

            return new MilestoneResponse(milestone.month(), milestone.name(), milestone.goal(), state);
        }

    }

    public record ActivityResponse(
            int no,
            int weekNo,
            String date,
            String startTime,
            int minutes,
            String title,
            String guide,
            String status) {

        public static ActivityResponse from(GoalActivityEntity activity) {
            return new ActivityResponse(activity.getNo(), activity.getWeekNo(), activity.getActDate().toString(),
                    StartTimes.format(activity.getStartTime()), activity.getMinutes(), activity.getTitle(),
                    activity.getGuide(), activity.getStatus().name());
        }

    }

}
