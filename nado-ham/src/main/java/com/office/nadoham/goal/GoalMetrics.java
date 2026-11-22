package com.office.nadoham.goal;

// 목표 실행 캘린더의 성과 지표(관리자 화면). 비율은 0~1 이며, 계산할 대상이 아직 없으면 null 이다.
// pending(관찰 중)은 기준 기간이 아직 끝나지 않아 성공·실패를 정할 수 없는 사용자로, 비율 계산에서 뺀다.
public record GoalMetrics(String asOf, Recovery recovery, FirstActivity firstActivity, Completion completion, Activities activities) {

    // 핵심 지표: 예정된 활동을 한 번 이상 놓친 사용자 중, 첫 미실행 다음 날부터 7일 안에 활동을 한 번 이상 완료한 사용자 비율
    // (5분만 하고 완료한 것도 포함). missedUsers = recovered + notRecovered + pending
    public record Recovery(int missedUsers, int recovered, int notRecovered, int pending, Double rate) {
    }

    // 보조 지표 1: 첫 계획을 확정한 사용자(첫 목표의 첫 활동 날짜가 오늘까지 온 사용자) 중 첫 활동을 완료한 비율
    public record FirstActivity(int users, int completed, int notCompleted, int pending, Double rate) {
    }

    // 보조 지표 2: 과정 기간이 끝났거나 마무리한 사용자 중 1개월 과정을 마무리한 사용자 비율
    public record Completion(int endedUsers, int completedUsers, Double rate) {
    }

    // 전체 활동 상태 수와, 완료 중 '5분만 하고 완료'의 비중(축소 실행 비율)
    public record Activities(long planned, long done, long shortDone, long skipped, Double shortShare) {
    }

}
