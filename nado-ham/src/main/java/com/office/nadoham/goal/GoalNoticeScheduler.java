package com.office.nadoham.goal;

import com.office.nadoham.telegram.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

// 1분마다 목표 알림(활동 시각 알림)을 보낼 차례인지 확인한다. 시각은 goal.notify.cron 으로 바꿀 수 있고,
// goal.notify.enabled=false 로 끌 수 있다(여러 서버에서 띄울 때 한 곳에서만 켠다).
@Slf4j
@Component
public class GoalNoticeScheduler {

    private final GoalNoticeService goalNoticeService;
    private final TelegramProperties telegramProperties;
    private final Clock clock;
    private final boolean enabled;

    public GoalNoticeScheduler(GoalNoticeService goalNoticeService, TelegramProperties telegramProperties, Clock clock,
                               @Value("${goal.notify.enabled:true}") boolean enabled) {
        this.goalNoticeService = goalNoticeService;
        this.telegramProperties = telegramProperties;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${goal.notify.cron:0 * * * * *}", zone = "Asia/Seoul")
    public void sendNotices() {
        if (!enabled || !telegramProperties.isConfigured()) {
            return;
        }

        try {
            goalNoticeService.sendDueReminders(LocalDateTime.now(clock));

        } catch (RuntimeException e) {
            // 한 번 실패해도 다음 분의 실행은 계속되어야 한다. 예외 메시지는 남기지 않고 종류만 남긴다
            log.error("목표 알림 실행 중 오류: {}", e.getClass().getSimpleName());
        }
    }

}
