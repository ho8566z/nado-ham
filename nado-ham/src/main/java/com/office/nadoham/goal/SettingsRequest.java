package com.office.nadoham.goal;

// 목표 실행 조건 수정 요청: 주당 횟수(1~7), 1회 소요 시간(5~120분), 시작 시각(HH:mm)
public record SettingsRequest(Integer weeklyCount, Integer minutes, String startTime) {
}
