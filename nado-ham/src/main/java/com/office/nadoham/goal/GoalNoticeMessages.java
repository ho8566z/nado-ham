package com.office.nadoham.goal;

import java.time.LocalTime;
import java.util.List;

// 목표 알림 문구. 시간과 바로 할 행동을 함께 담는다. 일반 텍스트(parse_mode 없음)이고 메시지에 일정 제목은 넣지 않는다.
public final class GoalNoticeMessages {

    private GoalNoticeMessages() {
    }

    // 활동 시각 알림: "[운동] 저녁 7시 30분이에요. 걷기 20분: 편한 속도로 20분 걷기. 20분이면 돼요. ..."
    public static List<String> reminder(String theme, LocalTime startTime, int minutes, String title, String guide, String shortGuide) {
        String text = "[" + theme + "] " + timeLabel(startTime) + "이에요.\n"
                + title + ": " + guide + " " + minutes + "분이면 돼요.\n"
                + "시간이 빠듯하면 5분만 해도 괜찮아요. " + shortGuide;

        return List.of(text);
    }

    // 주말 회고 알림: 누적 진전을 보여 주고 다음 주 계획을 앱에서 확인하게 한다
    public static List<String> retro(String theme, RetroResponse retro) {
        StringBuilder text = new StringBuilder("[" + theme + "] " + retro.message());
        text.append(retro.finalWeek()
                ? "\n앱 오늘 탭에서 한 달 과정을 마무리해 주세요."
                : "\n앱 오늘 탭에서 다음 주 계획을 확인해 주세요.");

        return List.of(text.toString());
    }

    // "저녁 7시 30분", 정각이면 "저녁 7시"
    static String timeLabel(LocalTime time) {
        int hour = time.getHour();
        String period;
        if (hour < 5) {
            period = "새벽";
        } else if (hour < 11) {
            period = "아침";
        } else if (hour < 13) {
            period = "낮";
        } else if (hour < 18) {
            period = "오후";
        } else if (hour < 21) {
            period = "저녁";
        } else {
            period = "밤";
        }

        int clockHour = hour % 12 == 0 ? 12 : hour % 12;
        String minute = time.getMinute() == 0 ? "" : " " + time.getMinute() + "분";

        return period + " " + clockHour + "시" + minute;
    }

}
