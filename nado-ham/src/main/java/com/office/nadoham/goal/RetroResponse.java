package com.office.nadoham.goal;

// 주말 회고 한 건: 끝난 주(weekNo)의 실천 결과와 다음 주 제안. finalWeek 이면 마지막 주라 다음 주 대신 과정 마무리를 한다.
// planned 는 그 주에 계획한 활동 수, done 은 완료, shortDone 은 5분만 하고 완료, skipped 는 건너뜀, missed 는 아직 예정 상태로 남은(놓친) 수다.
public record RetroResponse(
        int goalNo,
        String theme,
        String courseName,
        int weekNo,
        int planned,
        int done,
        int shortDone,
        int skipped,
        int missed,
        boolean finalWeek,
        int suggestedWeeklyCount,
        int suggestedMinutes,
        String message) {
}
