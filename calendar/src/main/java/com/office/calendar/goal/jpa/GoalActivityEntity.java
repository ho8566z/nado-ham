package com.office.calendar.goal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

// 목표에 속한 활동 한 번. 날짜·시각은 DATE/TIME 으로 저장한다(일정 PLAN 의 정수 년·월·일 한계를 물려받지 않는다).
@Entity
@Table(name = "GOAL_ACTIVITY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NO")
    private int no;

    @Column(name = "GOAL_NO", nullable = false)
    private int goalNo;

    @Column(name = "WEEK_NO", nullable = false)
    private int weekNo;

    @Column(name = "ACT_DATE", nullable = false)
    private LocalDate actDate;

    @Column(name = "START_TIME", nullable = false)
    private LocalTime startTime;

    @Column(name = "MINUTES", nullable = false)
    private int minutes;

    @Column(name = "TITLE", nullable = false, length = 100)
    private String title;

    @Column(name = "GUIDE", nullable = false, length = 300)
    private String guide;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 12, columnDefinition = "VARCHAR(12)")
    private ActivityStatus status;

    @Builder
    public GoalActivityEntity(int goalNo, int weekNo, LocalDate actDate, LocalTime startTime,
                              int minutes, String title, String guide) {
        this.goalNo = goalNo;
        this.weekNo = weekNo;
        this.actDate = actDate;
        this.startTime = startTime;
        this.minutes = minutes;
        this.title = title;
        this.guide = guide;
        this.status = ActivityStatus.PLANNED;
    }

}
