package com.office.calendar.planner.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanDatesTest {

    @Test
    @DisplayName("실제로 있는 날짜는 유효하다(윤년 2/29 포함)")
    void validDates() {
        assertThat(PlanDates.isValid(2026, 11, 8)).isTrue();
        assertThat(PlanDates.isValid(2028, 2, 29)).isTrue();
        assertThat(PlanDates.isValid(2026, 12, 31)).isTrue();
    }

    @Test
    @DisplayName("없는 날짜(2/31, 평년 2/29, 9/0, 13월, 0으로 빈 값)는 유효하지 않다")
    void invalidDates() {
        assertThat(PlanDates.isValid(2026, 2, 31)).isFalse();
        assertThat(PlanDates.isValid(2027, 2, 29)).isFalse();
        assertThat(PlanDates.isValid(2026, 9, 0)).isFalse();
        assertThat(PlanDates.isValid(2026, 13, 1)).isFalse();
        assertThat(PlanDates.isValid(0, 0, 0)).isFalse();
    }

}
