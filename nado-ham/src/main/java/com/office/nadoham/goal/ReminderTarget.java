package com.office.nadoham.goal;

import java.time.LocalDate;
import java.time.LocalTime;

// 활동 시각 알림을 받을 활동 한 건: 알림을 켜고 텔레그램이 연동된 USER 의 예정 활동
public record ReminderTarget(
        int activityNo,
        int goalNo,
        long chatId,
        String courseCode,
        String title,
        String guide,
        int minutes,
        LocalDate actDate,
        LocalTime startTime) {
}
