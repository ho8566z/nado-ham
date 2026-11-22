package com.office.nadoham.goal;

import java.util.List;

// 목표 과정 하나. 처음 1개월 과정은 주차별 활동(weeks)을 쓰고, 3·6·12개월 과정은 네 개의 월 단계(stages: 기초→연습→적용→마무리)를
// 기간에 맞게 늘려 쓴다. 직접 쓴 weeks 가 없는 과정은 단계의 활동을 주차마다 하나씩 쓴다.
// personal 과정은 사용자가 쓴 목표 한 문장({goal} 자리)으로 활동 이름·안내를 채운다.
public record Course(
        String code,
        String theme,
        String subTheme,
        String name,
        String goal,
        String checkpoint,
        int defaultWeeklyCount,
        int defaultMinutes,
        String shortGuide,
        List<List<Session>> weeks,
        List<Stage> stages,
        boolean personal) {

    public static final String GOAL_SLOT = "{goal}";

    public Course {
        if (weeks == null) {
            weeks = stages.stream().map(Stage::sessions).toList();
        }
    }

    // shortGuide 는 '5분만 하기'에서 보여 주는 같은 목표의 짧은 버전이다.
    // 활동 한 번의 이름과 오늘 할 일 안내
    public record Session(String title, String guide) {
    }

    // 월 단계 하나: 그 달의 중간 목표와 활동 세 가지
    public record Stage(String name, String goal, List<Session> sessions) {
    }

    // 해당 주차(1부터)의 활동 목록(처음 시작하는 1개월 과정)
    public List<Session> sessionsOfWeek(int week) {
        return weeks.get(week - 1);
    }

}
