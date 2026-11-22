package com.office.nadoham.goal;

// 활동을 다른 날로 옮기는 요청. 날짜는 yyyy-MM-dd, 시각은 HH:mm 이며 시각을 비우면 원래 시각을 유지한다.
public record ActivityMoveRequest(String date, String time) {
}
