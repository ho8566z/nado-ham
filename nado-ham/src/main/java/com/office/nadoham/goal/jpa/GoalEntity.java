package com.office.nadoham.goal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// 사용자의 목표 하나(과정 선택 + 실행 조건). 소유자는 일정(PLAN)과 같은 로그인 아이디로 구분한다.
@Entity
@Table(name = "GOAL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NO")
    private int no;

    @Column(name = "OWNER_ID", nullable = false)
    private String ownerId;

    @Column(name = "COURSE_CODE", nullable = false, length = 30)
    private String courseCode;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "WEEKLY_COUNT", nullable = false)
    private int weeklyCount;

    @Column(name = "MINUTES", nullable = false)
    private int minutes;

    // 활동을 시작하는 시각(하나의 시간대). 활동별 시각은 GOAL_ACTIVITY 에 따로 저장한다
    @Column(name = "PREFERRED_TIME", nullable = false)
    private LocalTime preferredTime;

    // 기간(1·3·6·12개월). 기존 목표(1개월 과정)는 기본값 1로 채워진다
    @Column(name = "MONTHS", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 1")
    private int months;

    // 현재 수준(BEGINNER·EXPERIENCED). 문자열로 저장한다
    @Column(name = "PLAN_LEVEL", nullable = false, length = 12, columnDefinition = "VARCHAR(12) NOT NULL DEFAULT 'BEGINNER'")
    private String level;

    // 개인 목표 과정에서 사용자가 쓴 목표 한 문장(그 밖의 과정은 비어 있다)
    @Column(name = "PERSONAL_GOAL", length = 100)
    private String personalGoal;

    // 문자열로 저장한다(BRIEF_LOG.RESULT 와 같은 이유: Hibernate 가 MySQL ENUM 으로 만들지 않게)
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private GoalStatus status;

    // 일시 중지한 날(중지 중에만 값이 있다). 다시 시작할 때 쉰 일수를 계산하는 기준이다
    @Column(name = "PAUSED_AT")
    private LocalDate pausedAt;

    @Column(name = "REG_DATE")
    private LocalDateTime regDate;

    @Builder
    public GoalEntity(String ownerId, String courseCode, LocalDate startDate, LocalDate endDate,
                      int weeklyCount, int minutes, LocalTime preferredTime,
                      int months, String level, String personalGoal) {
        this.ownerId = ownerId;
        this.courseCode = courseCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.weeklyCount = weeklyCount;
        this.minutes = minutes;
        this.preferredTime = preferredTime;
        this.months = months <= 0 ? 1 : months;
        this.level = level == null ? "BEGINNER" : level;
        this.personalGoal = personalGoal;
        this.status = GoalStatus.ACTIVE;
    }

    // 다음 주 계획을 확정할 때 그 주의 횟수·시간·시각을 목표에 반영한다(화면의 '주 n회' 표시와 다음 제안의 기준)
    public void adjustPlan(int weeklyCount, int minutes, LocalTime preferredTime) {
        this.weeklyCount = weeklyCount;
        this.minutes = minutes;
        this.preferredTime = preferredTime;
    }

    // 늦게 시작한 주가 원래 종료일을 넘으면 종료일을 늘린다(줄이지는 않는다)
    public void extendEndDate(LocalDate date) {
        if (date.isAfter(endDate)) {
            this.endDate = date;
        }
    }

    // 종료일을 직접 바꾼다(검증은 서비스에서 한다). 전체 주 수는 그대로이고, 종료일이 다음 주 시작보다 앞이면 그 주가 마지막 주가 된다
    public void changeEndDate(LocalDate date) {
        this.endDate = date;
    }

    // 일시 중지
    public void pause(LocalDate today) {
        this.status = GoalStatus.PAUSED;
        this.pausedAt = today;
    }

    // 다시 시작: 상태를 진행 중으로 돌리고 쉰 일수(0 이상)를 돌려준다. 종료일도 그만큼 늦춘다
    public long resume(LocalDate today) {
        long pausedDays = pausedAt == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(pausedAt, today));
        this.status = GoalStatus.ACTIVE;
        this.pausedAt = null;
        this.endDate = endDate.plusDays(pausedDays);

        return pausedDays;
    }

    // 한 달 과정을 마친 목표
    public void complete() {
        this.status = GoalStatus.DONE;
    }

    @PrePersist
    private void prePersist() {
        regDate = LocalDateTime.now();
    }

}
