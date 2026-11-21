package com.office.calendar.goal;

import java.util.List;

// 첫 주 계획 미리보기와 계획 시작에 쓰는 요청. 미리보기는 days(가능한 요일), 시작은 slots(사용자가 확인한 날짜·시각)를 쓴다.
// 요일은 1(월) ~ 7(일). 날짜는 yyyy-MM-dd, 시각은 HH:mm.
public record GoalRequest(
        String courseCode,
        String startDate,
        Integer weeklyCount,
        Integer minutes,
        String startTime,
        List<Integer> days,
        List<Slot> slots) {

    public record Slot(String date, String time) {
    }

}
