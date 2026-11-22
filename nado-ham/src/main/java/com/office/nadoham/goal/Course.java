package com.office.nadoham.goal;

import java.util.List;

// 목표 과정 하나(1개월 과정). 주차별 활동 목록과 4주 차에 확인할 결과를 가진다.
public record Course(
        String code,
        String theme,
        String name,
        String goal,
        String checkpoint,
        int defaultWeeklyCount,
        int defaultMinutes,
        String shortGuide,
        List<List<Session>> weeks) {

    // shortGuide 는 '5분만 하기'에서 보여 주는 같은 목표의 짧은 버전이다.
    // 활동 한 번의 이름과 오늘 할 일 안내
    public record Session(String title, String guide) {
    }

    // 해당 주차(1부터)의 활동 목록
    public List<Session> sessionsOfWeek(int week) {
        return weeks.get(week - 1);
    }

}
