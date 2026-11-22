package com.office.nadoham.goal;

// 달력에 표시할 목표 활동 한 건. day 는 그 달의 일, conflict 는 같은 시간대에 일정(PLAN)이 있다는 표시(예정 상태만 계산한다)다.
public record CalendarItem(
        int activityNo,
        int goalNo,
        String courseCode,
        String theme,
        int day,
        String startTime,
        int minutes,
        String title,
        String status,
        boolean conflict,
        String conflictTitle) {
}
