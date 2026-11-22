package com.office.nadoham.goal;

import java.util.List;

// 과정 선택 화면에 보여 줄 과정 요약(주차별 활동 내용은 담지 않는다). months 는 고를 수 있는 기간이다.
public record CourseSummary(
        String code,
        String theme,
        String subTheme,
        String name,
        String goal,
        int defaultWeeklyCount,
        int defaultMinutes,
        boolean personal,
        List<Integer> months) {

    public static CourseSummary from(Course course) {
        return new CourseSummary(course.code(), course.theme(), course.subTheme(), course.name(), course.goal(),
                course.defaultWeeklyCount(), course.defaultMinutes(), course.personal(), CoursePlan.MONTH_OPTIONS);
    }

}
