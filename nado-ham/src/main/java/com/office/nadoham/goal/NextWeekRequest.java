package com.office.nadoham.goal;

import java.util.List;

// 다음 주 계획 미리보기·확정 요청. weekNo 는 사용자가 회고한 끝난 주(RetroResponse.weekNo)이며,
// 이미 다음 주 계획을 만든 뒤 같은 요청이 다시 오는 것을 알아보는 데 쓴다.
// 미리보기는 days(가능한 요일 1월~7일), 확정은 slots(사용자가 확인한 날짜·시각)를 쓴다.
public record NextWeekRequest(
        Integer weekNo,
        Integer weeklyCount,
        Integer minutes,
        String startTime,
        List<Integer> days,
        List<GoalRequest.Slot> slots) {
}
