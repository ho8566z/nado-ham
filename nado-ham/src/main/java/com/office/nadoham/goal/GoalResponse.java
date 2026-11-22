package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.GoalActivityEntity;
import com.office.nadoham.planner.util.StartTimes;

import java.util.List;

// 내 목표 한 건과 그 활동들
public record GoalResponse(
        int no,
        String courseCode,
        String theme,
        String courseName,
        String checkpoint,
        String startDate,
        String endDate,
        int weeklyCount,
        int minutes,
        String status,
        List<ActivityResponse> activities) {

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
