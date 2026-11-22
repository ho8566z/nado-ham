package com.office.nadoham.goal.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 목표 알림 발송 기록. 같은 알림을 두 번 보내지 않게 하는 선점 표이기도 하다.
// (KIND, GOAL_NO, REF_NO, SCHEDULED_AT)이 유니크라 먼저 INSERT 한 쪽만 보낼 수 있다.
// REF_NO 는 REMINDER 면 활동 번호, RETRO 면 회고 대상 주차다. SCHEDULED_AT 은 알림이 가리키는 시각이라
// 활동을 다른 날로 옮기면 새 알림으로 취급된다.
@Entity
@Table(name = "GOAL_NOTICE", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"KIND", "GOAL_NO", "REF_NO", "SCHEDULED_AT"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoalNoticeEntity {

    public static final int ERROR_MSG_MAX = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NO")
    private int no;

    // 문자열로 저장한다(MySQL ENUM 방지, BRIEF_LOG.RESULT 와 같은 이유)
    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private NoticeKind kind;

    @Column(name = "GOAL_NO", nullable = false)
    private int goalNo;

    @Column(name = "REF_NO", nullable = false)
    private int refNo;

    @Column(name = "SCHEDULED_AT", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private NoticeStatus status;

    @Column(name = "ATTEMPTS", nullable = false)
    private int attempts;

    @Column(name = "LAST_ATTEMPT_AT")
    private LocalDateTime lastAttemptAt;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    // 실패 사유. 봇 토큰·요청 URL·메시지 본문·chat ID 는 넣지 않는다
    @Column(name = "ERROR_MSG", length = ERROR_MSG_MAX)
    private String errorMsg;

    // 처음 선점: 발송을 시작하는 상태로 만든다
    @Builder
    public GoalNoticeEntity(NoticeKind kind, int goalNo, int refNo, LocalDateTime scheduledAt, LocalDateTime now) {
        this.kind = kind;
        this.goalNo = goalNo;
        this.refNo = refNo;
        this.scheduledAt = scheduledAt;
        this.status = NoticeStatus.SENDING;
        this.attempts = 1;
        this.lastAttemptAt = now;
    }

    public void markSent(LocalDateTime now) {
        this.status = NoticeStatus.SENT;
        this.sentAt = now;
        this.errorMsg = null;
    }

    // 다시 시도할 수 있는 실패. 시도 횟수를 다 썼으면 포기로 바꾼다
    public void markFailed(String message, int maxAttempts) {
        this.status = attempts >= maxAttempts ? NoticeStatus.GIVEUP : NoticeStatus.FAILED;
        this.errorMsg = truncate(message);
    }

    public void giveUp(String message) {
        this.status = NoticeStatus.GIVEUP;
        this.errorMsg = truncate(message);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > ERROR_MSG_MAX ? text.substring(0, ERROR_MSG_MAX) : text;
    }

}
