package com.aiworkmate.service;

import com.aiworkmate.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HalfDayCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 27);

    @Test
    void shouldCalculateInclusiveHalfDaySlots() {
        assertThat(HalfDayCalculator.calculate(MONDAY, "AM", MONDAY, "AM")).isEqualTo(1);
        assertThat(HalfDayCalculator.calculate(MONDAY, "AM", MONDAY, "PM")).isEqualTo(2);
        assertThat(HalfDayCalculator.calculate(MONDAY, "PM", MONDAY, "PM")).isEqualTo(1);
    }

    @Test
    void shouldIncludeWeekendBecausePhaseOneUsesCalendarDays() {
        LocalDate friday = LocalDate.of(2026, 7, 31);
        LocalDate monday = LocalDate.of(2026, 8, 3);
        assertThat(HalfDayCalculator.calculate(friday, "AM", monday, "PM")).isEqualTo(8);
    }

    @Test
    void shouldRejectReverseRange() {
        assertThatThrownBy(() -> HalfDayCalculator.calculate(MONDAY, "PM", MONDAY, "AM"))
                .isInstanceOf(BusinessException.class);
    }
}
