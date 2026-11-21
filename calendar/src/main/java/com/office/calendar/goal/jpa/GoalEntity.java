package com.office.calendar.goal.jpa;

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

    // 문자열로 저장한다(BRIEF_LOG.RESULT 와 같은 이유: Hibernate 가 MySQL ENUM 으로 만들지 않게)
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private GoalStatus status;

    @Column(name = "REG_DATE")
    private LocalDateTime regDate;

    @Builder
    public GoalEntity(String ownerId, String courseCode, LocalDate startDate, LocalDate endDate,
                      int weeklyCount, int minutes, LocalTime preferredTime) {
        this.ownerId = ownerId;
        this.courseCode = courseCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.weeklyCount = weeklyCount;
        this.minutes = minutes;
        this.preferredTime = preferredTime;
        this.status = GoalStatus.ACTIVE;
    }

    @PrePersist
    private void prePersist() {
        regDate = LocalDateTime.now();
    }

}
