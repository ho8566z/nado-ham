package com.office.nadoham.planner.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

// 일정 시작·종료 시간(plan-007) 값 처리. 화면과 주고받는 형식은 항상 "HH:mm"(24시간)이다.
public final class StartTimes {

    // 브라우저의 시간 입력은 보통 HH:mm, 초가 붙으면 HH:mm:ss로 온다. 둘 다 받는다.
    private static final Pattern INPUT = Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)(:[0-5]\\d)?$");
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("HH:mm");

    private StartTimes() {
    }

    // 요청으로 받은 값을 "HH:mm"으로 정리한다. 비었으면 null(시간 없음), 형식이 틀리면 IllegalArgumentException
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        if (!INPUT.matcher(value).matches()) {
            throw new IllegalArgumentException("시작 시간 형식이 맞지 않습니다: HH:mm");
        }

        return value.substring(0, 5);
    }

    // 종료 시간을 "HH:mm"으로 정리한다. 비었으면 null(종료 시간 없음). 시작 시간이 있어야 하고 시작보다 뒤여야 한다
    // (같은 날 안에서만 쓴다. 자정을 넘기는 일정은 종료 시간을 두지 않는다). 규칙에 맞지 않으면 IllegalArgumentException
    public static String normalizeEnd(String rawEnd, String normalizedStart) {
        String end;
        try {
            end = normalize(rawEnd);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("종료 시간 형식이 맞지 않습니다: HH:mm");
        }

        if (end == null) {
            return null;
        }
        if (normalizedStart == null) {
            throw new IllegalArgumentException("종료 시간은 시작 시간이 있어야 합니다.");
        }
        if (end.compareTo(normalizedStart) <= 0) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        return end;
    }

    // "HH:mm" 문자열을 시간으로(null이면 null)
    public static LocalTime toLocalTime(String hhmm) {
        return hhmm == null ? null : LocalTime.parse(hhmm);
    }

    // 시간을 "HH:mm" 문자열로(null이면 null)
    public static String format(LocalTime time) {
        return time == null ? null : time.format(OUTPUT);
    }

}
