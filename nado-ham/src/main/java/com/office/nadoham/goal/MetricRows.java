package com.office.nadoham.goal;

import java.time.LocalDate;

// 지표 계산용 조회 결과 행(MyBatis 가 채운다)
public final class MetricRows {

    private MetricRows() {
    }

    // 사용자별 첫 미실행일과, 그 다음 날부터 7일 안에 완료한 활동이 있는지
    public record MissedUser(String ownerId, LocalDate firstMiss, boolean recovered) {
    }

    // 사용자의 첫 목표에서 날짜가 가장 이른 활동
    public record FirstActivityRow(String ownerId, String status, LocalDate actDate) {
    }

    // 사용자별 과정을 마무리한 목표가 있는지, 과정 기간이 끝났(거나 마무리했)는지
    public record CourseUser(String ownerId, boolean completed, boolean ended) {
    }

    public record StatusCount(String status, long total) {
    }

}
