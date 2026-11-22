package com.office.nadoham.goal;

import java.util.List;

// 오늘 탭 데이터. cards 는 오늘 활동(예정 시각순, 이미 끝낸 것 포함), missed 는 예정일이 지났는데 아직 예정 상태인 활동,
// next 는 오늘 이후 가장 가까운 예정 활동(없으면 null), activeGoals 는 진행 중인 목표 수다.
public record TodayResponse(
        String today,
        List<Card> cards,
        List<Card> missed,
        Card next,
        int activeGoals) {

    // 활동 한 건과 그 목표의 이번 주 진행도(weeklyCount 회 중 weeklyDone 회)
    public record Card(
            int activityNo,
            int goalNo,
            String theme,
            String courseName,
            String date,
            String startTime,
            int minutes,
            String title,
            String guide,
            String shortGuide,
            String status,
            int weeklyCount,
            int weeklyDone) {
    }

}
