package com.office.calendar.planner.util;

import java.time.DateTimeException;
import java.time.LocalDate;

// 일정 날짜 검증(T-0025). 일정은 년·월·일을 정수 세 개로 저장하므로 2/31, 9/0 같은 값을 서버가 직접 걸러야 한다.
public final class PlanDates {

    private PlanDates() {
    }

    // 실제로 있는 날짜인지(윤년 포함)
    public static boolean isValid(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

}
