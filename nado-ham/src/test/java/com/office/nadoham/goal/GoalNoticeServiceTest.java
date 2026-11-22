package com.office.nadoham.goal;

import com.office.nadoham.brief.BriefSender;
import com.office.nadoham.config.TimeConfig;
import com.office.nadoham.goal.GoalNoticeService.DispatchResult;
import com.office.nadoham.goal.jpa.GoalNoticeEntity;
import com.office.nadoham.goal.jpa.GoalNoticeRepository;
import com.office.nadoham.goal.jpa.NoticeKind;
import com.office.nadoham.goal.jpa.NoticeStatus;
import com.office.nadoham.goal.mapper.GoalNoticeMapper;
import com.office.nadoham.telegram.TelegramApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 알림 중복 방지(선점)·재시도·포기·결과 불명 처리를 DB 없이 확인한다
class GoalNoticeServiceTest {

    // 2026-11-23(월) 19:30 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T10:30:00Z"), TimeConfig.ZONE);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 19, 30);
    private static final long CHAT = 777L;
    private static final int MAX_ATTEMPTS = 3;

    private GoalNoticeMapper mapper;
    private GoalNoticeRepository repository;
    private BriefSender sender;
    private GoalRetroService retroService;
    private GoalNoticeService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(GoalNoticeMapper.class);
        repository = Mockito.mock(GoalNoticeRepository.class);
        sender = Mockito.mock(BriefSender.class);
        retroService = Mockito.mock(GoalRetroService.class);
        service = new GoalNoticeService(mapper, repository, sender, retroService, CLOCK, 30, 0, MAX_ATTEMPTS, 3, 10, 21);

        when(repository.findByKindAndGoalNoAndRefNoAndScheduledAt(any(), anyInt(), anyInt(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(GoalNoticeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ReminderTarget target(int activityNo, String courseCode) {
        return new ReminderTarget(activityNo, 1, CHAT, courseCode, "걷기 20분", "편한 속도로 20분 걷기.", 20,
                LocalDate.of(2026, 11, 23), LocalTime.of(19, 30));
    }

    private GoalNoticeEntity existing(NoticeStatus status, int attempts, LocalDateTime lastAttempt) {
        GoalNoticeEntity entity = GoalNoticeEntity.builder().kind(NoticeKind.REMINDER).goalNo(1).refNo(5)
                .scheduledAt(NOW).now(lastAttempt).build();
        ReflectionTestUtils.setField(entity, "no", 9);
        ReflectionTestUtils.setField(entity, "status", status);
        ReflectionTestUtils.setField(entity, "attempts", attempts);
        return entity;
    }

    @Test
    @DisplayName("보낼 차례인 알림은 먼저 선점(SENDING)하고 보낸 뒤 SENT 로 기록한다")
    void sendsAndMarksSent() {
        when(mapper.selectDueReminders(any(), any(), anyInt(), any())).thenReturn(List.of(target(5, "EXERCISE_HABIT")));

        GoalNoticeService.RunSummary summary = service.sendDueReminders(NOW);

        assertThat(summary).isEqualTo(new GoalNoticeService.RunSummary(1, 1, 0, 0));
        ArgumentCaptor<GoalNoticeEntity> saved = ArgumentCaptor.forClass(GoalNoticeEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(NoticeKind.REMINDER);
        assertThat(saved.getValue().getRefNo()).isEqualTo(5);
        assertThat(saved.getValue().getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 11, 23, 19, 30));
        verify(sender).send(eq(CHAT), anyList());
        verify(repository).save(saved.getValue());
        assertThat(saved.getValue().getStatus()).isEqualTo(NoticeStatus.SENT);
        assertThat(saved.getValue().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("조회 범위는 (지금 - 허용 지연) ~ (지금 + 미리 알림)이고 재시도 기준 시각을 함께 넘기며, 발송 중으로 오래 남은 알림을 먼저 정리한다")
    void queryWindowAndStaleCleanup() {
        when(mapper.selectDueReminders(any(), any(), anyInt(), any())).thenReturn(List.of());

        service.sendDueReminders(NOW);

        verify(mapper).selectDueReminders(NOW.minusMinutes(30), NOW, MAX_ATTEMPTS, NOW.minusMinutes(3));
        verify(repository).giveUpStale(NOW.minusMinutes(10), GoalNoticeService.STALE_MESSAGE, NoticeStatus.SENDING, NoticeStatus.GIVEUP);
    }

    @Test
    @DisplayName("이미 보냈거나 포기했거나 발송 중인 알림은 다시 보내지 않는다")
    void doesNotResendHandled() {
        for (NoticeStatus status : List.of(NoticeStatus.SENT, NoticeStatus.GIVEUP, NoticeStatus.SENDING)) {
            when(repository.findByKindAndGoalNoAndRefNoAndScheduledAt(any(), anyInt(), anyInt(), any()))
                    .thenReturn(Optional.of(existing(status, 1, NOW.minusMinutes(20))));

            assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.SKIPPED);
        }

        verify(sender, never()).send(anyLong(), anyList());
        verify(repository, never()).claimRetry(anyInt(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("동시에 실행돼 INSERT 가 유니크 제약에 걸리면 먼저 선점한 쪽이 보내므로 보내지 않는다")
    void losesRace() {
        when(repository.saveAndFlush(any(GoalNoticeEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.SKIPPED);
        verify(sender, never()).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("재시도: 실패했고 대기 시간이 지났으면 조건부 UPDATE 로 다시 선점해 보낸다")
    void retriesFailedAfterBackoff() {
        GoalNoticeEntity failed = existing(NoticeStatus.FAILED, 1, NOW.minusMinutes(5));
        GoalNoticeEntity reclaimed = existing(NoticeStatus.SENDING, 2, NOW);
        when(repository.findByKindAndGoalNoAndRefNoAndScheduledAt(any(), anyInt(), anyInt(), any())).thenReturn(Optional.of(failed));
        when(repository.claimRetry(9, NOW, MAX_ATTEMPTS, NoticeStatus.SENDING, NoticeStatus.FAILED)).thenReturn(1);
        when(repository.findById(9)).thenReturn(Optional.of(reclaimed));

        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.SENT);
        assertThat(reclaimed.getStatus()).isEqualTo(NoticeStatus.SENT);
    }

    @Test
    @DisplayName("재시도: 대기 시간 안이면 보내지 않고, 다른 곳이 먼저 다시 선점했으면(UPDATE 0건) 보내지 않는다")
    void retryRespectsBackoffAndRace() {
        when(repository.findByKindAndGoalNoAndRefNoAndScheduledAt(any(), anyInt(), anyInt(), any()))
                .thenReturn(Optional.of(existing(NoticeStatus.FAILED, 1, NOW.minusMinutes(1))));
        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.SKIPPED);
        verify(repository, never()).claimRetry(anyInt(), any(), anyInt(), any(), any());

        when(repository.findByKindAndGoalNoAndRefNoAndScheduledAt(any(), anyInt(), anyInt(), any()))
                .thenReturn(Optional.of(existing(NoticeStatus.FAILED, 1, NOW.minusMinutes(5))));
        when(repository.claimRetry(anyInt(), any(), anyInt(), any(), any())).thenReturn(0);
        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.SKIPPED);
        verify(sender, never()).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("네트워크 오류·요청 제한은 FAILED 로 두어 다시 시도하고, 시도 횟수를 다 쓰면 GIVEUP 이 된다")
    void retryableFailureThenGiveUp() {
        doThrow(new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0)).when(sender).send(anyLong(), anyList());

        ArgumentCaptor<GoalNoticeEntity> saved = ArgumentCaptor.forClass(GoalNoticeEntity.class);
        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.FAILED);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NoticeStatus.FAILED);
        assertThat(saved.getValue().getErrorMsg()).contains("연결 오류");

        GoalNoticeEntity lastTry = existing(NoticeStatus.SENDING, MAX_ATTEMPTS, NOW);
        lastTry.markFailed("실패", MAX_ATTEMPTS);
        assertThat(lastTry.getStatus()).isEqualTo(NoticeStatus.GIVEUP);
    }

    @Test
    @DisplayName("봇 차단(403)은 다시 시도하지 않고 바로 GIVEUP 으로 기록한다")
    void blockedGivesUpImmediately() {
        doThrow(new TelegramApiException(TelegramApiException.BLOCKED, 0)).when(sender).send(anyLong(), anyList());

        ArgumentCaptor<GoalNoticeEntity> saved = ArgumentCaptor.forClass(GoalNoticeEntity.class);
        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.GIVEUP);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NoticeStatus.GIVEUP);
        assertThat(saved.getValue().getErrorMsg()).contains("403");
    }

    @Test
    @DisplayName("예상 못 한 예외는 메시지를 남기지 않고 종류만 기록하며 FAILED 로 둔다")
    void unexpectedExceptionRecordsOnlyType() {
        doThrow(new IllegalStateException("secret-title-and-chat-777")).when(sender).send(anyLong(), anyList());

        ArgumentCaptor<GoalNoticeEntity> saved = ArgumentCaptor.forClass(GoalNoticeEntity.class);
        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.FAILED);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getErrorMsg()).isEqualTo("처리 중 오류(IllegalStateException)").doesNotContain("secret");
    }

    @Test
    @DisplayName("결과 기록 저장이 실패해도 예외를 밖으로 내보내지 않는다(행은 발송 중으로 남아 나중에 포기로 정리된다)")
    void saveFailureDoesNotThrow() {
        when(repository.save(any(GoalNoticeEntity.class))).thenThrow(new IllegalStateException("db down"));

        assertThat(service.dispatch(NoticeKind.REMINDER, 1, 5, NOW, CHAT, List.of("x"), NOW)).isEqualTo(DispatchResult.SENT);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지 알림은 계속 보낸다")
    void oneFailureDoesNotBlockOthers() {
        when(mapper.selectDueReminders(any(), any(), anyInt(), any()))
                .thenReturn(List.of(target(5, "EXERCISE_HABIT"), target(6, "GUITAR_SONG")));
        doThrow(new TelegramApiException(TelegramApiException.NETWORK_ERROR, 0)).doNothing().when(sender).send(anyLong(), anyList());

        GoalNoticeService.RunSummary summary = service.sendDueReminders(NOW);

        assertThat(summary).isEqualTo(new GoalNoticeService.RunSummary(2, 1, 1, 0));
    }

    @Test
    @DisplayName("알 수 없는 과정 코드의 활동은 건너뛴다")
    void unknownCourseSkipped() {
        when(mapper.selectDueReminders(any(), any(), anyInt(), any())).thenReturn(List.of(target(5, "NOPE")));

        assertThat(service.sendDueReminders(NOW)).isEqualTo(new GoalNoticeService.RunSummary(1, 0, 0, 1));
        verify(sender, never()).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("정리 중 오류가 나도 알림 발송은 계속된다")
    void staleCleanupFailureIsIgnored() {
        when(repository.giveUpStale(any(), any(), any(), any())).thenThrow(new IllegalStateException("db"));
        when(mapper.selectDueReminders(any(), any(), anyInt(), any())).thenReturn(List.of(target(5, "EXERCISE_HABIT")));

        assertThat(service.sendDueReminders(NOW).sent()).isEqualTo(1);
    }

    private RetroResponse retro(boolean finalWeek) {
        return new RetroResponse(1, "운동", "습관", finalWeek ? 4 : 1, "2026-11-22", 3, 2, 0, 0, 1, finalWeek, 3, 20,
                "이번 주 3회 중 2회 실천했어요.");
    }

    @Test
    @DisplayName("회고 알림: 그 주 마지막 날 21시부터 보내고, 주차를 대상 번호·21시를 알림 시각으로 선점한다")
    void retroSentAfterRetroHour() {
        when(mapper.selectNoticeGoals()).thenReturn(List.of(new NoticeGoal(1, "alice", CHAT)));
        when(retroService.pendingOf(1)).thenReturn(Optional.of(retro(false)));
        LocalDateTime evening = LocalDateTime.of(2026, 11, 22, 21, 5);

        GoalNoticeService.RunSummary summary = service.sendDueRetros(evening);

        assertThat(summary).isEqualTo(new GoalNoticeService.RunSummary(1, 1, 0, 0));
        ArgumentCaptor<GoalNoticeEntity> saved = ArgumentCaptor.forClass(GoalNoticeEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getKind()).isEqualTo(NoticeKind.RETRO);
        assertThat(saved.getValue().getRefNo()).isEqualTo(1);
        assertThat(saved.getValue().getScheduledAt()).isEqualTo(LocalDateTime.of(2026, 11, 22, 21, 0));
        verify(sender).send(eq(CHAT), anyList());
    }

    @Test
    @DisplayName("회고 알림: 21시 전이거나 24시간이 지났거나 회고할 차례가 아니면 보내지 않는다")
    void retroOutsideWindow() {
        when(mapper.selectNoticeGoals()).thenReturn(List.of(new NoticeGoal(1, "alice", CHAT)));
        when(retroService.pendingOf(1)).thenReturn(Optional.of(retro(false)));

        assertThat(service.sendDueRetros(LocalDateTime.of(2026, 11, 22, 20, 59)).targets()).isZero();
        assertThat(service.sendDueRetros(LocalDateTime.of(2026, 11, 23, 21, 1)).targets()).isZero();

        when(retroService.pendingOf(1)).thenReturn(Optional.empty());
        assertThat(service.sendDueRetros(LocalDateTime.of(2026, 11, 22, 21, 30)).targets()).isZero();
        verify(sender, never()).send(anyLong(), anyList());
    }

    @Test
    @DisplayName("회고 알림: 이미 보낸 회고는 다음 분에 다시 보내지 않는다")
    void retroNotRepeated() {
        when(mapper.selectNoticeGoals()).thenReturn(List.of(new NoticeGoal(1, "alice", CHAT)));
        when(retroService.pendingOf(1)).thenReturn(Optional.of(retro(false)));
        when(repository.findByKindAndGoalNoAndRefNoAndScheduledAt(any(), anyInt(), anyInt(), any()))
                .thenReturn(Optional.of(existing(NoticeStatus.SENT, 1, NOW)));

        GoalNoticeService.RunSummary summary = service.sendDueRetros(LocalDateTime.of(2026, 11, 22, 21, 30));

        assertThat(summary).isEqualTo(new GoalNoticeService.RunSummary(1, 0, 0, 1));
        verify(sender, never()).send(anyLong(), anyList());
    }

}
