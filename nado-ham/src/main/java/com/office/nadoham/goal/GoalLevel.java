package com.office.nadoham.goal;

import java.util.Optional;

// 현재 수준: 처음 시작하면 기초부터, 해 본 적이 있으면 연습 단계부터 계획한다.
public enum GoalLevel {

    BEGINNER,
    EXPERIENCED;

    // 비어 있으면 처음 시작으로 본다. 모르는 값은 비워 돌려준다
    public static Optional<GoalLevel> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(BEGINNER);
        }
        try {
            return Optional.of(valueOf(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
