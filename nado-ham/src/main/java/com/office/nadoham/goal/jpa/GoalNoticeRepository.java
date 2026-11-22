package com.office.nadoham.goal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GoalNoticeRepository extends JpaRepository<GoalNoticeEntity, Integer> {

    Optional<GoalNoticeEntity> findByKindAndGoalNoAndRefNoAndScheduledAt(
            NoticeKind kind, int goalNo, int refNo, LocalDateTime scheduledAt);

    // 실패한 알림을 다시 선점한다. 조건을 만족하는 한 쪽만 1을 돌려받는다(동시 실행 방지)
    @Modifying
    @Transactional
    @Query("update GoalNoticeEntity n set n.status = :sending, n.attempts = n.attempts + 1, n.lastAttemptAt = :now "
            + "where n.no = :no and n.status = :failed and n.attempts < :maxAttempts")
    int claimRetry(@Param("no") int no, @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts,
                   @Param("sending") NoticeStatus sending, @Param("failed") NoticeStatus failed);

    // 발송 중으로 남은 지 오래된 알림(서버가 보내는 도중 멈춘 경우)은 보냈는지 알 수 없어 다시 보내지 않고 포기로 바꾼다
    @Modifying
    @Transactional
    @Query("update GoalNoticeEntity n set n.status = :giveUp, n.errorMsg = :message "
            + "where n.status = :sending and n.lastAttemptAt < :before")
    int giveUpStale(@Param("before") LocalDateTime before, @Param("message") String message,
                    @Param("sending") NoticeStatus sending, @Param("giveUp") NoticeStatus giveUp);

}
