package com.office.nadoham.goal.jpa;

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
import java.time.LocalDateTime;
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

    // 처음 예정일을 놓친 날(예정일이 지난 뒤에 옮기거나 짧게 하거나 건너뛸 때 기록). 핵심 지표(놓친 뒤 7일 안 재개) 계산용
    @Column(name = "MISSED_DATE")
    private LocalDate missedDate;

    @Column(name = "DONE_AT")
    private LocalDateTime doneAt;

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

    // 완료 기록. 짧게 하고 끝냈으면 SHORT_DONE 으로 구분해 저장한다(회고에서 축소 실행을 확인할 수 있게)
    public void complete(boolean shortVersion, LocalDate today, LocalDateTime now) {
        recordMiss(today);
        this.status = shortVersion ? ActivityStatus.SHORT_DONE : ActivityStatus.DONE;
        this.doneAt = now;
    }

    // 같은 활동의 날짜·시각만 바꾼다
    public void reschedule(LocalDate date, LocalTime time, LocalDate today) {
        recordMiss(today);
        this.actDate = date;
        this.startTime = time;
    }

    public void skip(LocalDate today) {
        if (missedDate == null) {
            missedDate = actDate;
        }
        this.status = ActivityStatus.SKIPPED;
    }

    private void recordMiss(LocalDate today) {
        if (actDate.isBefore(today) && missedDate == null) {
            missedDate = actDate;
        }
    }

}
