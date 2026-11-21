package com.office.calendar.goal;

// 첫 주 계획 미리보기의 활동 한 건. conflict 는 같은 시간대에 이미 있는 일정이 있다는 표시다(확정은 사용자가 정한다).
public record ActivityPlan(
        String date,
        String startTime,
        int minutes,
        String title,
        String guide,
        boolean conflict,
        String conflictTitle) {
}
