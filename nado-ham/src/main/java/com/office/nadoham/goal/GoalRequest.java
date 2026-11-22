package com.office.nadoham.goal;

import java.util.List;

// 첫 주 계획 미리보기와 계획 시작에 쓰는 요청. 미리보기는 days(가능한 요일), 시작은 slots(사용자가 확인한 날짜·시각)를 쓴다.
// months 는 기간(1·3·6·12, 비우면 1), level 은 현재 수준(BEGINNER·EXPERIENCED, 비우면 BEGINNER), personalGoal 은 개인 목표 과정의 목표 한 문장이다.
// 요일은 1(월) ~ 7(일). 날짜는 yyyy-MM-dd, 시각은 HH:mm.
public record GoalRequest(
        String courseCode,
        String startDate,
        Integer weeklyCount,
        Integer minutes,
        String startTime,
        List<Integer> days,
        List<Slot> slots,
        Integer months,
        String level,
        String personalGoal) {

    public GoalRequest(String courseCode, String startDate, Integer weeklyCount, Integer minutes, String startTime,
                       List<Integer> days, List<Slot> slots) {
        this(courseCode, startDate, weeklyCount, minutes, startTime, days, slots, null, null, null);
    }

    // title 은 개인 목표 과정에서 사용자가 고친 활동 이름이다(다른 과정은 서버가 정한 이름을 쓴다)
    public record Slot(String date, String time, String title) {

        public Slot(String date, String time) {
            this(date, time, null);
        }

    }

}
