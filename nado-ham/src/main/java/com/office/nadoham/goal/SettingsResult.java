package com.office.nadoham.goal;

import java.util.List;

// 실행 조건 수정 결과: 바뀐 목표와 활동, 횟수를 늘려 새로 만든 활동(같은 시간대 일정과 겹치면 표시), 횟수를 줄여 지운 활동 수
public record SettingsResult(GoalResponse goal, List<ActivityPlan> added, int removed) {
}
