package com.office.nadoham.goal;

import com.office.nadoham.brief.BriefSender;
import com.office.nadoham.goal.jpa.GoalNoticeEntity;
import com.office.nadoham.goal.jpa.GoalNoticeRepository;
import com.office.nadoham.goal.jpa.NoticeKind;
import com.office.nadoham.goal.jpa.NoticeStatus;
import com.office.nadoham.goal.mapper.GoalNoticeMapper;
import com.office.nadoham.telegram.TelegramApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 목표 알림(활동 시각 알림·주말 회고)을 텔레그램으로 보낸다.
//
// 중복을 막는 방법: 보내기 전에 GOAL_NOTICE 에 (종류, 목표, 대상, 알림 시각) 행을 먼저 INSERT 해 '선점'한다.
// 유니크 제약 때문에 동시에 실행돼도 한 쪽만 선점하고, 선점한 쪽만 보낸다.
// 복구 정책: 실패해도 다시 시도할 수 있는 것(네트워크·요청 제한·서버 오류)은 FAILED 로 두고 일정 간격으로 최대 횟수까지 다시 보낸다.
// 봇 차단(403)은 다시 보내지 않는다. 발송 중(SENDING)으로 오래 남은 것은 보냈는지 알 수 없으므로 중복 발송보다
// 누락이 낫다고 보고 다시 보내지 않고 포기(GIVEUP)로 바꾼다.
// 사용자마다 DB 저장이 따로 끝나도록 이 클래스 전체를 하나의 트랜잭션으로 묶지 않는다.
// 메시지 본문(일정 제목)과 chat ID 는 로그에 남기지 않는다.
@Slf4j
@Service
public class GoalNoticeService {

    static final int RETRO_WINDOW_HOURS = 24;       // 회고 알림은 정해진 시각부터 이 시간 안에만 보낸다(너무 늦은 알림은 보내지 않는다)
    static final String STALE_MESSAGE = "발송 결과를 알 수 없어 다시 보내지 않음";

    public enum DispatchResult { SENT, FAILED, GIVEUP, SKIPPED }

    // 한 번 실행한 결과 요약
    public record RunSummary(int targets, int sent, int failed, int skipped) {
    }

    private final GoalNoticeMapper goalNoticeMapper;
    private final GoalNoticeRepository goalNoticeRepository;
    private final BriefSender briefSender;
    private final GoalRetroService goalRetroService;
    private final Clock clock;
    private final int maxDelayMinutes;
    private final int leadMinutes;
    private final int maxAttempts;
    private final int retryBackoffMinutes;
    private final int staleSendingMinutes;
    private final int retroHour;

    public GoalNoticeService(GoalNoticeMapper goalNoticeMapper, GoalNoticeRepository goalNoticeRepository,
                             BriefSender briefSender, GoalRetroService goalRetroService, Clock clock,
                             @Value("${goal.notify.max-delay-minutes:30}") int maxDelayMinutes,
                             @Value("${goal.notify.lead-minutes:0}") int leadMinutes,
                             @Value("${goal.notify.max-attempts:3}") int maxAttempts,
                             @Value("${goal.notify.retry-backoff-minutes:3}") int retryBackoffMinutes,
                             @Value("${goal.notify.stale-sending-minutes:10}") int staleSendingMinutes,
                             @Value("${goal.notify.retro-hour:21}") int retroHour) {
        this.goalNoticeMapper = goalNoticeMapper;
        this.goalNoticeRepository = goalNoticeRepository;
        this.briefSender = briefSender;
        this.goalRetroService = goalRetroService;
        this.clock = clock;
        this.maxDelayMinutes = Math.max(1, maxDelayMinutes);
        this.leadMinutes = Math.max(0, leadMinutes);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMinutes = Math.max(1, retryBackoffMinutes);
        this.staleSendingMinutes = Math.max(1, staleSendingMinutes);
        this.retroHour = Math.min(23, Math.max(0, retroHour));
    }

    // now 기준으로 보낼 차례인 활동 시각 알림을 보낸다. 활동 시각이 (now - 허용 지연) ~ (now + 미리 알림) 인 예정 활동이 대상이다.
    public RunSummary sendDueReminders(LocalDateTime now) {
        giveUpStale(now);

        List<ReminderTarget> targets = goalNoticeMapper.selectDueReminders(
                now.minusMinutes(maxDelayMinutes), now.plusMinutes(leadMinutes), maxAttempts, now.minusMinutes(retryBackoffMinutes));

        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (ReminderTarget target : targets) {
            DispatchResult result = dispatchReminder(target, now);
            switch (result) {
                case SENT -> sent++;
                case SKIPPED -> skipped++;
                default -> failed++;
            }
        }

        if (!targets.isEmpty()) {
            log.info("활동 알림을 처리했다: 대상 {}건, 발송 {}, 실패 {}, 건너뜀 {}", targets.size(), sent, failed, skipped);
        }
        return new RunSummary(targets.size(), sent, failed, skipped);
    }

    // 회고할 차례인 목표의 주말 회고 알림을 보낸다. 그 주 마지막 날의 retro-hour 시부터 24시간 안이 대상이다.
    // 사용자가 앱에서 다음 주 계획을 만들면 회고가 없어지므로 더 보내지 않는다.
    public RunSummary sendDueRetros(LocalDateTime now) {
        List<NoticeGoal> goals = goalNoticeMapper.selectNoticeGoals();

        int targets = 0;
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (NoticeGoal goal : goals) {
            RetroResponse retro = goalRetroService.pendingOf(goal.goalNo()).orElse(null);
            if (retro == null) {
                continue;
            }

            LocalDateTime scheduledAt = LocalDate.parse(retro.weekEnd()).atTime(retroHour, 0);
            if (now.isBefore(scheduledAt) || now.isAfter(scheduledAt.plusHours(RETRO_WINDOW_HOURS))) {
                continue;
            }

            targets++;
            DispatchResult result = dispatch(NoticeKind.RETRO, goal.goalNo(), retro.weekNo(), scheduledAt, goal.chatId(),
                    GoalNoticeMessages.retro(retro.theme(), retro), now);
            switch (result) {
                case SENT -> sent++;
                case SKIPPED -> skipped++;
                default -> failed++;
            }
        }

        if (targets > 0) {
            log.info("회고 알림을 처리했다: 대상 {}건, 발송 {}, 실패 {}, 건너뜀 {}", targets, sent, failed, skipped);
        }
        return new RunSummary(targets, sent, failed, skipped);
    }

    // 발송 중으로 오래 남은 알림을 포기로 바꾼다
    public void giveUpStale(LocalDateTime now) {
        try {
            int changed = goalNoticeRepository.giveUpStale(now.minusMinutes(staleSendingMinutes), STALE_MESSAGE,
                    NoticeStatus.SENDING, NoticeStatus.GIVEUP);
            if (changed > 0) {
                log.warn("발송 결과를 알 수 없는 알림 {}건을 포기로 바꿨다", changed);
            }
        } catch (RuntimeException e) {
            log.warn("오래된 발송 중 알림을 정리하지 못했다: {}", e.getClass().getSimpleName());
        }
    }

    private DispatchResult dispatchReminder(ReminderTarget target, LocalDateTime now) {
        Course course = CourseCatalog.find(target.courseCode()).orElse(null);
        if (course == null) {
            return DispatchResult.SKIPPED;
        }

        List<String> parts = GoalNoticeMessages.reminder(course.theme(), target.startTime(), target.minutes(),
                target.title(), target.guide(), course.shortGuide());
        LocalDateTime scheduledAt = target.actDate().atTime(target.startTime());

        return dispatch(NoticeKind.REMINDER, target.goalNo(), target.activityNo(), scheduledAt, target.chatId(), parts, now);
    }

    // 선점 → 발송 → 결과 기록. 어떤 예외도 밖으로 내보내지 않는다
    DispatchResult dispatch(NoticeKind kind, int goalNo, int refNo, LocalDateTime scheduledAt, long chatId,
                            List<String> parts, LocalDateTime now) {
        GoalNoticeEntity notice = claim(kind, goalNo, refNo, scheduledAt, now);
        if (notice == null) {
            return DispatchResult.SKIPPED;
        }

        DispatchResult result;
        try {
            briefSender.send(chatId, parts);
            notice.markSent(LocalDateTime.now(clock));
            result = DispatchResult.SENT;

        } catch (TelegramApiException e) {
            if (e.isBlocked()) {
                notice.giveUp("봇 차단 (HTTP 403)");
                result = DispatchResult.GIVEUP;
            } else {
                notice.markFailed(e.getMessage(), maxAttempts);
                result = notice.getStatus() == NoticeStatus.GIVEUP ? DispatchResult.GIVEUP : DispatchResult.FAILED;
            }

        } catch (RuntimeException e) {
            // 메시지에 제목·chat ID 가 섞일 수 있어 예외 메시지는 쓰지 않고 종류만 남긴다
            notice.markFailed("처리 중 오류(" + e.getClass().getSimpleName() + ")", maxAttempts);
            result = notice.getStatus() == NoticeStatus.GIVEUP ? DispatchResult.GIVEUP : DispatchResult.FAILED;
        }

        save(notice);
        return result;
    }

    // 알림을 선점한다. 이미 처리했거나 다른 곳이 먼저 선점했으면 null
    private GoalNoticeEntity claim(NoticeKind kind, int goalNo, int refNo, LocalDateTime scheduledAt, LocalDateTime now) {
        try {
            GoalNoticeEntity existing = goalNoticeRepository
                    .findByKindAndGoalNoAndRefNoAndScheduledAt(kind, goalNo, refNo, scheduledAt).orElse(null);

            if (existing == null) {
                return goalNoticeRepository.saveAndFlush(GoalNoticeEntity.builder()
                        .kind(kind).goalNo(goalNo).refNo(refNo).scheduledAt(scheduledAt).now(now).build());
            }

            if (existing.getStatus() != NoticeStatus.FAILED
                    || existing.getLastAttemptAt() == null
                    || existing.getLastAttemptAt().isAfter(now.minusMinutes(retryBackoffMinutes))) {
                return null;
            }

            int claimed = goalNoticeRepository.claimRetry(existing.getNo(), now, maxAttempts, NoticeStatus.SENDING, NoticeStatus.FAILED);
            if (claimed != 1) {
                return null;
            }

            // 갱신된 시도 횟수 등을 반영해 다시 읽는다
            return goalNoticeRepository.findById(existing.getNo()).orElse(null);

        } catch (DataIntegrityViolationException e) {
            log.info("다른 곳에서 먼저 선점한 알림이라 건너뛴다");
            return null;

        } catch (RuntimeException e) {
            log.warn("알림을 선점하지 못했다: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    // 결과를 저장한다. 저장에 실패하면 행이 SENDING 으로 남고, 시간이 지나면 포기로 바뀌어 다시 보내지 않는다
    private void save(GoalNoticeEntity notice) {
        try {
            goalNoticeRepository.save(notice);
        } catch (RuntimeException e) {
            log.warn("알림 결과를 기록하지 못했다: {}", e.getClass().getSimpleName());
        }
    }

}
