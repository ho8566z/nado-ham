package com.office.nadoham.goal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 알림 문구: 시간대 표현과 바로 할 행동·5분 버전 안내
class GoalNoticeMessagesTest {

    @Test
    @DisplayName("시간대 표현: 저녁 7시 30분처럼 12시간제와 분(정각이면 생략)으로 쓴다")
    void timeLabel() {
        assertThat(GoalNoticeMessages.timeLabel(LocalTime.of(19, 30))).isEqualTo("저녁 7시 30분");
        assertThat(GoalNoticeMessages.timeLabel(LocalTime.of(7, 0))).isEqualTo("아침 7시");
        assertThat(GoalNoticeMessages.timeLabel(LocalTime.of(0, 5))).isEqualTo("새벽 12시 5분");
        assertThat(GoalNoticeMessages.timeLabel(LocalTime.of(12, 0))).isEqualTo("낮 12시");
        assertThat(GoalNoticeMessages.timeLabel(LocalTime.of(15, 45))).isEqualTo("오후 3시 45분");
        assertThat(GoalNoticeMessages.timeLabel(LocalTime.of(23, 59))).isEqualTo("밤 11시 59분");
    }

    @Test
    @DisplayName("활동 시각 알림: 시간·바로 할 행동·소요 시간·5분 버전을 한 메시지에 담는다")
    void reminder() {
        List<String> parts = GoalNoticeMessages.reminder("공부", LocalTime.of(19, 30), 15,
                "주문 표현 익히기", "주문 표현 3개를 소리 내어 말하고 한 번 녹음하기.", "표현 1개만 소리 내어 3번 말해 보기.");

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).contains("[공부] 저녁 7시 30분이에요.")
                .contains("주문 표현 익히기: 주문 표현 3개를 소리 내어 말하고 한 번 녹음하기. 15분이면 돼요.")
                .contains("5분만 해도 괜찮아요. 표현 1개만 소리 내어 3번 말해 보기.");
    }

    @Test
    @DisplayName("회고 알림: 결과 문구 뒤에 앱에서 할 일을 안내하고, 마지막 주는 과정 마무리를 안내한다")
    void retro() {
        RetroResponse middle = new RetroResponse(1, "운동", "습관", 1, 3, 2, 0, 0, 1, false, 3, 20, "이번 주 3회 중 2회 실천했어요.");
        RetroResponse last = new RetroResponse(1, "운동", "습관", 4, 3, 3, 0, 0, 0, true, 3, 20, "한 달을 마쳤어요.");

        assertThat(GoalNoticeMessages.retro("운동", middle).get(0)).startsWith("[운동] 이번 주 3회 중 2회 실천했어요.").contains("다음 주 계획을 확인");
        assertThat(GoalNoticeMessages.retro("운동", last).get(0)).contains("한 달 과정을 마무리");
    }

}
