package com.office.nadoham.goal;

// 회고 알림 후보: 알림을 켜고 텔레그램이 연동된 USER 의 진행 중 목표
public record NoticeGoal(int goalNo, String ownerId, long chatId) {
}
